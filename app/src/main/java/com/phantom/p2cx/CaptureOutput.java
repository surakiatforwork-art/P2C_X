package com.phantom.p2cx;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class CaptureOutput {
    private final File file;
    private final OutputStream stream;
    private final ContentResolver resolver;
    private final Uri collection;
    private final ContentValues values;

    CaptureOutput(Object options) throws ReflectiveOperationException {
        file = fieldOfType(options, File.class);
        stream = fieldOfType(options, OutputStream.class);
        resolver = fieldOfType(options, ContentResolver.class);
        collection = fieldOfType(options, Uri.class);
        values = fieldOfType(options, ContentValues.class);
        if (file == null && stream == null && (resolver == null || collection == null)) {
            throw new IllegalArgumentException("Unknown CameraX output options");
        }
    }

    File file() { return file; }

    Uri write(Context context, Uri source) throws IOException {
        return write(context, source, () -> true);
    }

    Uri write(Context context, Uri source, java.util.function.BooleanSupplier active) throws IOException {
        if (!active.getAsBoolean()) throw new IOException("Image selection cancelled");
        if ("com.phantom.p2cx.images".equals(source.getAuthority())) return save(context, source, active);
        File normalized = File.createTempFile("p2cx-selected-", ".jpg", context.getCacheDir());
        android.graphics.Bitmap bitmap = null;
        try {
            bitmap = Images.decode(context, source, 4096);
            try (OutputStream output = new FileOutputStream(normalized)) { Images.jpeg(bitmap, output); }
            bitmap.recycle();
            bitmap = null;
            if (!active.getAsBoolean()) throw new IOException("P2C_X switched off");
            return save(context, Uri.fromFile(normalized), active);
        } finally {
            if (bitmap != null) bitmap.recycle();
            normalized.delete();
        }
    }

    private Uri save(Context context, Uri source, java.util.function.BooleanSupplier active) throws IOException {
        if (file != null) {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) throw new IOException("Missing output directory");
            File temporary = File.createTempFile("p2cx-", ".jpg", parent);
            try {
                try (OutputStream output = new FileOutputStream(temporary)) { copy(context, source, output); }
                if (!active.getAsBoolean()) throw new IOException("P2C_X switched off");
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return Uri.fromFile(file);
            } finally {
                temporary.delete();
            }
        }
        if (stream != null) {
            copy(context, source, stream);
            return null;
        }
        Uri inserted = resolver.insert(collection, values == null ? new ContentValues() : new ContentValues(values));
        if (inserted == null) throw new IOException("Cannot create output image");
        try {
            try (OutputStream output = resolver.openOutputStream(inserted, "w")) {
                if (output == null) throw new IOException("Cannot open output image");
                copy(context, source, output);
            }
            if (android.os.Build.VERSION.SDK_INT >= 29 && values != null && values.containsKey("is_pending")) {
                ContentValues published = new ContentValues();
                published.put("is_pending", 0);
                resolver.update(inserted, published, null, null);
            }
            return inserted;
        } catch (IOException | RuntimeException error) {
            try { resolver.delete(inserted, null, null); } catch (RuntimeException ignored) { }
            throw error;
        }
    }

    static void copy(Context context, Uri source, OutputStream output) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IOException("Cannot read selected image");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        }
    }

    static <T> T fieldOfType(Object object, Class<T> type) throws IllegalAccessException {
        for (Class<?> current = object.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                Object value = field.get(object);
                if (type.isInstance(value)) return type.cast(value);
            }
        }
        return null;
    }
}

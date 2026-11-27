package com.phantom.p2cx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class EditorRenderer {
    private EditorRenderer() { }

    static int[] dimensions(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth < 1 || bounds.outHeight < 1) throw new IOException("Cannot read image size");
        int orientation = 1;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        } catch (IOException ignored) { }
        return orientation >= 5 && orientation <= 8
            ? new int[]{bounds.outHeight, bounds.outWidth} : new int[]{bounds.outWidth, bounds.outHeight};
    }

    static File render(Context context, Uri uri, EditorState state, int sourceWidth, int sourceHeight) throws IOException {
        if (state.width < 1 || state.height < 1 || state.width > 8192 || state.height > 8192
            || (long) state.width * state.height > 24000000L) {
            throw new IOException("Use 1-8192 pixels per side and at most 24 megapixels");
        }
        int orientedWidth = state.imageWidth(sourceWidth, sourceHeight), orientedHeight = state.imageHeight(sourceWidth, sourceHeight);
        float scale = Math.min(1f, Math.max(state.width / (state.crop.width() * orientedWidth),
            state.height / (state.crop.height() * orientedHeight)));
        int maxEdge = Math.max(1, (int) Math.ceil(Math.max(sourceWidth, sourceHeight) * scale));
        if (sourceWidth * (double) sourceHeight * scale * scale > 24000000) {
            throw new IOException("Source decode too large. Reduce output size");
        }
        Bitmap source = null;
        Bitmap output = null;
        File file = null;
        boolean complete = false;
        try {
            source = Images.decode(context, uri, maxEdge);
            output = Bitmap.createBitmap(state.width, state.height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.WHITE);
            int imageWidth = state.imageWidth(source.getWidth(), source.getHeight());
            int imageHeight = state.imageHeight(source.getWidth(), source.getHeight());
            canvas.scale(state.width / (imageWidth * state.crop.width()), state.height / (imageHeight * state.crop.height()));
            canvas.translate(-imageWidth * state.crop.left, -imageHeight * state.crop.top);
            EditorTransform.draw(canvas, source, state, new android.graphics.RectF(0, 0, imageWidth, imageHeight),
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            file = new File(context.getCacheDir(), java.util.UUID.randomUUID() + ".jpg");
            try (FileOutputStream stream = new FileOutputStream(file)) { Images.jpeg(output, stream); }
            complete = true;
            return file;
        } finally {
            if (source != null) source.recycle();
            if (output != null) output.recycle();
            if (!complete && file != null) file.delete();
        }
    }
}

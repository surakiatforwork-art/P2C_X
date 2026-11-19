package com.phantom.p2cx;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises shipped DEX contracts in an isolated loader, without opening target apps. */
public final class ContractChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle results = new Bundle();
        StringBuilder report = new StringBuilder();
        try {
            Context context = getTargetContext();
            File source = new File(context.getCacheDir(), UUID.randomUUID() + ".jpg");
            Bitmap bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xffb34e20);
            try (FileOutputStream stream = new FileOutputStream(source)) { Images.jpeg(bitmap, stream); }
            bitmap.recycle();
            Uri shared = Uri.fromFile(source);
            Bitmap decoded = Images.decode(context, shared, 256);
            check(decoded.getWidth() == 40 && decoded.getHeight() == 30, "JPEG decode");
            decoded.recycle();
            modernSettings(context, report);
            editor(context, report);
            galleryUi(context, report);
            imageKeeper(context, shared, report);
            ghostShift(context, shared, report);
            nera(context, shared, report);
            source.delete();
            results.putString("stream", "\nPASS\n" + report);
            finish(-1, results);
        } catch (Throwable error) {
            results.putString("stream", "\nFAIL\n" + report + android.util.Log.getStackTraceString(error));
            finish(0, results);
        }
    }

    private void galleryUi(Context context, StringBuilder report) throws Exception {
        String permission = android.os.Build.VERSION.SDK_INT >= 33 ? android.Manifest.permission.READ_MEDIA_IMAGES : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            report.append("Gallery UI skipped: full image permission not granted\n"); return;
        }
        android.app.Activity gallery = startActivitySync(new android.content.Intent(context, GalleryActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        android.app.Activity editor = null;
        try {
            android.widget.GridView grid = (android.widget.GridView) findView(gallery.getWindow().getDecorView(), android.widget.GridView.class);
            long deadline = android.os.SystemClock.uptimeMillis() + 8000;
            while (grid.getCount() == 0 && android.os.SystemClock.uptimeMillis() < deadline) { android.os.SystemClock.sleep(50); waitForIdleSync(); }
            check(grid.getCount() > 0, "Gallery contains accessible images");
            android.app.Instrumentation.ActivityMonitor monitor = addMonitor(EditorActivity.class.getName(), null, false);
            runOnMainSync(() -> grid.performItemClick(grid.getChildAt(0), 0, 0));
            editor = waitForMonitorWithTimeout(monitor, 8000); removeMonitor(monitor);
            check(editor != null && editor.getIntent().getData() != null, "Single tap opens selected image in editor");
            Field previewField = EditorActivity.class.getDeclaredField("preview"); previewField.setAccessible(true);
            deadline = android.os.SystemClock.uptimeMillis() + 8000;
            while (previewField.get(editor) == null && android.os.SystemClock.uptimeMillis() < deadline) { android.os.SystemClock.sleep(50); waitForIdleSync(); }
            check(previewField.get(editor) != null, "Selected image preview loaded");
            Field resetField = EditorActivity.class.getDeclaredField("reset"); resetField.setAccessible(true);
            final android.view.View reset = (android.view.View) resetField.get(editor);
            runOnMainSync(() -> reset.performClick());
            Field stateField = EditorActivity.class.getDeclaredField("state"); stateField.setAccessible(true);
            EditorState state = (EditorState) stateField.get(editor);
            check("original".equals(state.ratio) && !state.flipX && !state.flipY && !state.customSize && state.crop.equals(new android.graphics.RectF(0, 0, 1, 1)), "Reset restores original frame and dimensions");
            report.append("Gallery single-tap editor, selected image preview and UI reset passed\n");
        } finally {
            final android.app.Activity active = editor;
            runOnMainSync(() -> { if (active != null) active.finish(); gallery.finish(); }); waitForIdleSync();
        }
    }
    private android.view.View findView(android.view.View view, Class<?> type) {
        if (type.isInstance(view)) return view;
        if (view instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) {
            android.view.View found = findView(((android.view.ViewGroup) view).getChildAt(i), type); if (found != null) return found;
        }
        return null;
    }

    private void modernSettings(Context context, StringBuilder report) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 8000;
        while (!ModuleSettings.shared(context) && android.os.SystemClock.uptimeMillis() < deadline) {
            android.os.SystemClock.sleep(50); waitForIdleSync();
        }
        check(ModuleSettings.shared(context), "Modern framework service connected");
        Field field = ModuleSettings.class.getDeclaredField("service"); field.setAccessible(true);
        io.github.libxposed.service.XposedService service = (io.github.libxposed.service.XposedService) field.get(null);
        android.content.SharedPreferences remote = service.getRemotePreferences("state");
        boolean enabled = ModuleSettings.enabled(context), boot = ModuleSettings.startOnBoot(context);
        try {
            ModuleSettings.setEnabled(context, false);
            check(remote.getBoolean("configured", false) && !remote.getBoolean("enabled", true), "Remote disable persisted");
            ModuleSettings.setEnabled(context, true);
            check(remote.getBoolean("enabled", false), "Remote enable persisted");
            ModuleSettings.setStartOnBoot(context, false);
            new BootReceiver().onReceive(context, new android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED));
            check(!remote.getBoolean("enabled", true), "Boot disabled setting");
            ModuleSettings.setStartOnBoot(context, true);
            new BootReceiver().onReceive(context, new android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED));
            check(remote.getBoolean("enabled", false), "Boot enabled setting");
        } finally {
            ModuleSettings.setStartOnBoot(context, boot);
            ModuleSettings.setEnabled(context, enabled);
        }
        report.append("Modern service connection, remote on/off settings and boot handler passed\n");
    }

    private void editor(Context context, StringBuilder report) throws Exception {
        android.content.SharedPreferences prefs = context.getSharedPreferences("editor-contract", 0);
        prefs.edit().clear().commit();
        EditorState state = EditorState.load(prefs);
        state.fit(80, 40);
        check("original".equals(state.ratio) && state.width == 80 && state.height == 40, "Original ratio default");
        state.ratio = "1:1"; state.fit(80, 40);
        check(Math.abs(state.crop.width() * 80 - state.crop.height() * 40) < .1, "Square crop");
        state.flipX = true; state.customSize = true; state.width = 20; state.height = 20; state.save(prefs);
        EditorState loaded = EditorState.load(prefs); loaded.fit(40, 80);
        check(loaded.flipX && loaded.width == 20 && loaded.height == 20 && loaded.crop.right <= 1, "Saved settings on a portrait source");
        loaded.flipX = false;
        check(EditorState.load(prefs).flipX, "Unconfirmed edits do not persist");
        Bitmap image = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(image);
        canvas.drawColor(android.graphics.Color.RED);
        android.graphics.Paint paint = new android.graphics.Paint(); paint.setColor(android.graphics.Color.BLUE);
        canvas.drawRect(40, 0, 80, 40, paint);
        File source = new File(context.getCacheDir(), "editor-contract.png");
        try (FileOutputStream stream = new FileOutputStream(source)) { image.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        image.recycle();
        state = new EditorState(); state.fit(80, 40); state.flipX = true;
        state.ratio = "free"; state.crop.set(0, 0, .5f, 1); state.width = 24; state.height = 24;
        File rendered = EditorRenderer.render(context, Uri.fromFile(source), state, 80, 40);
        Bitmap output = android.graphics.BitmapFactory.decodeFile(rendered.getAbsolutePath());
        int pixel = output.getPixel(12, 12);
        check(output.getWidth() == 24 && output.getHeight() == 24 && android.graphics.Color.blue(pixel) > 200 && android.graphics.Color.red(pixel) < 50, "Mirror then crop and resize");
        output.recycle();
        Uri shared = Uri.parse("content://com.phantom.p2cx.images/" + rendered.getName());
        try (java.io.InputStream stream = context.getContentResolver().openInputStream(shared)) { check(stream != null && stream.read() == 255, "Provider reads exported JPEG"); }
        state.width = 0;
        try { EditorRenderer.render(context, Uri.fromFile(source), state, 80, 40); throw new AssertionError("Zero size accepted"); }
        catch (java.io.IOException expected) { }
        source.delete(); rendered.delete(); prefs.edit().clear().commit();
        report.append("Editor original ratio, saved settings, unconfirmed edits, mirror/crop/resize, provider and invalid size passed\n");
    }

    private void imageKeeper(Context context, Uri source, StringBuilder report) throws Exception {
        ClassLoader loader = loader(context, "com.branchphotovault");
        check(DexCaptureScanner.find(context.getPackageManager().getApplicationInfo("com.branchphotovault", 0))
            .contains("androidx.camera.core.ImageCapture"), "Image Keeper discovery");
        File destination = new File(context.getCacheDir(), "contract-keeper.jpg");
        Class<?> builder = loader.loadClass("androidx.camera.core.ImageCapture$OutputFileOptions$Builder");
        Object options = builder.getMethod("build").invoke(builder.getConstructor(File.class).newInstance(destination));
        CaptureOutput output = new CaptureOutput(options);
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        Class<?> contract = loader.loadClass("androidx.camera.core.ImageCapture$OnImageSavedCallback");
        loader.loadClass("androidx.camera.core.ImageCapture").getDeclaredMethod("takePicture", options.getClass(),
            java.util.concurrent.Executor.class, contract);
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{contract}, (proxy, method, args) -> {
            if (method.getName().equals("onImageSaved")) saved.incrementAndGet();
            if (method.getName().equals("onError")) errors.incrementAndGet();
            return null;
        });
        CaptureCallback completion = CaptureCallback.create(callback, output);
        completion.success(output.write(context, source));
        completion.failure("cancel test");
        check(saved.get() == 1 && errors.get() == 1 && destination.length() > 0, "Image Keeper callback");
        destination.delete();
        report.append("Image Keeper file save, success and cancellation callbacks passed\n");
    }

    private void ghostShift(Context context, Uri source, StringBuilder report) throws Exception {
        ClassLoader loader = loader(context, "com.phantom.ghostshift");
        check(DexCaptureScanner.find(context.getPackageManager().getApplicationInfo("com.phantom.ghostshift", 0))
            .contains("y.f0"), "GhostShift discovery");
        File destination = new File(context.getCacheDir(), "contract-ghost.jpg");
        Object options = allocate(loader.loadClass("y.d0"));
        put(options, "a", destination);
        CaptureOutput output = new CaptureOutput(options);
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        Object callback = allocate(loader.loadClass("j6.f"));
        loader.loadClass("y.f0").getDeclaredMethod("F", options.getClass(), java.util.concurrent.Executor.class, callback.getClass());
        Object success = Proxy.newProxyInstance(loader, new Class<?>[]{loader.loadClass("x6.e")}, (proxy, method, args) -> {
            check(Uri.fromFile(destination).equals(args[0]), "GhostShift result URI");
            saved.incrementAndGet();
            return null;
        });
        Object failure = Proxy.newProxyInstance(loader, new Class<?>[]{loader.loadClass("x6.c")}, (proxy, method, args) -> {
            check(loader.loadClass("y.g0").isInstance(args[0]), "GhostShift error type");
            errors.incrementAndGet();
            return null;
        });
        put(callback, "a", failure);
        put(callback, "b", success);
        put(callback, "c", destination);
        put(callback, "d", false);
        CaptureCallback completion = CaptureCallback.create(callback, output);
        completion.success(output.write(context, source));
        completion.failure("cancel test");
        check(saved.get() == 1 && errors.get() == 1 && destination.length() > 0, "GhostShift callbacks");
        destination.delete();
        report.append("GhostShift obfuscated output, success and cancellation callbacks passed\n");
    }

    private void nera(Context context, Uri source, StringBuilder report) throws Exception {
        ClassLoader loader = loader(context, "com.mca.neraapp");
        check(DexCaptureScanner.find(context.getPackageManager().getApplicationInfo("com.mca.neraapp", 0))
            .contains("z.Z"), "Nera discovery");
        File destination = new File(context.getCacheDir(), "contract-nera.jpg");
        Object options = loader.loadClass("z.B").getConstructor(File.class).newInstance(destination);
        CaptureOutput output = new CaptureOutput(options);
        AtomicInteger saved = new AtomicInteger();
        Object callback = allocate(loader.loadClass("A4.M"));
        loader.loadClass("z.Z").getDeclaredMethod("K", options.getClass(), java.util.concurrent.Executor.class, callback.getClass());
        Object reply = Proxy.newProxyInstance(loader, new Class<?>[]{loader.loadClass("R6.l")}, (proxy, method, args) -> {
            saved.incrementAndGet();
            return null;
        });
        put(callback, "p", destination);
        put(callback, "q", reply);
        CaptureCallback completion = CaptureCallback.create(callback, output);
        completion.success(output.write(context, source));
        check(saved.get() == 1 && destination.length() > 0, "Nera result callback");
        destination.delete();
        report.append("Nera obfuscated output and Flutter result callback passed\n");
    }

    private static ClassLoader loader(Context context, String packageName) throws Exception {
        ApplicationInfo app = context.getPackageManager().getApplicationInfo(packageName, 0);
        String path = app.sourceDir;
        if (app.splitSourceDirs != null) for (String split : app.splitSourceDirs) path += File.pathSeparator + split;
        return new DexClassLoader(path, context.getCodeCacheDir().getPath(), null, context.getClassLoader());
    }


    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field instance = unsafeClass.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        return unsafeClass.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type);
    }

    private static void put(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
    }
}

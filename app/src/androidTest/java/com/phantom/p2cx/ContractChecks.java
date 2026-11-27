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
    private boolean controlOnly;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); controlOnly = arguments != null && "true".equals(arguments.getString("controlOnly")); start(); }

    @Override public void onStart() {
        Bundle results = new Bundle();
        StringBuilder report = new StringBuilder();
        android.content.SharedPreferences accessPrefs = getTargetContext().getSharedPreferences("access", 0);
        boolean hadDecision = accessPrefs.contains("decision"); String previousDecision = accessPrefs.getString("decision", "pending");
        String previousName = accessPrefs.getString("nickname", ""); boolean hadName = accessPrefs.contains("nickname");
        long previousSuccess = accessPrefs.getLong("last_success", 0);
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
            accessPolicy(context, report);
            if (!controlOnly) modernSettings(context, report);
            editor(context, report);
            recommendations(report);
            cropZoom(context, report);
            if (!controlOnly) galleryUi(context, report);
            if (!controlOnly) { imageKeeper(context, shared, report); ghostShift(context, shared, report); nera(context, shared, report); }
            else report.append("LSPosed and target-app callbacks skipped: control-only emulator run\n");
            source.delete();
            results.putString("stream", "\nPASS\n" + report);
            finish(-1, results);
        } catch (Throwable error) {
            results.putString("stream", "\nFAIL\n" + report + android.util.Log.getStackTraceString(error));
            finish(0, results);
        } finally {
            var edit = accessPrefs.edit();
            if (hadDecision) edit.putString("decision", previousDecision); else edit.remove("decision");
            if (hadName) edit.putString("nickname", previousName); else edit.remove("nickname");
            edit.putLong("last_success", previousSuccess).commit(); ModuleSettings.accessChanged(getTargetContext());
        }
    }

    private void accessPolicy(Context context, StringBuilder report) throws Exception {
        org.json.JSONObject approved = new org.json.JSONObject().put("enabled", true).put("allowed", true).put("deviceStatus", "approved").put("nickname", "QA");
        check(RemoteAccess.applyDecision(context, approved) && RemoteAccess.allowed(context), "Explicit approval applied");
        check(!RemoteAccess.applyDecision(context, null) && RemoteAccess.allowed(context), "Network failure preserves approved decision");
        check(!RemoteAccess.applyDecision(context, new org.json.JSONObject()) && RemoteAccess.allowed(context), "Malformed response preserves approval");
        org.json.JSONObject blocked = new org.json.JSONObject().put("enabled", true).put("allowed", false).put("deviceStatus", "blocked").put("nickname", "QA");
        check(RemoteAccess.applyDecision(context, blocked) && !RemoteAccess.allowed(context), "Explicit suspension applied");
        ModuleSettings.setEnabled(context, true);
        check(!context.getSharedPreferences("access", 0).getString("decision", "").equals("approved"), "User switch cannot approve license");
        check(!RemoteAccess.applyDecision(context, null), "Failed request is not a new decision");
        RemoteAccess.applyDecision(context, approved);
        report.append("Permission approval, suspension, switch separation, offline and malformed-response retention passed\n");
    }

    public static final class ResolutionFixture {
        final android.util.Size size = new android.util.Size(1920, 1080);
        final android.graphics.Rect crop = new android.graphics.Rect(0, 0, 1440, 1080);
        final int rotation = 90;
    }
    public static final class CaptureFixture {
        public ResolutionFixture renamedResolutionInfo() { return new ResolutionFixture(); }
    }
    private void recommendations(StringBuilder report) {
        android.content.Intent intent = CaptureRecommendation.attach(new android.content.Intent(), new CaptureFixture());
        check(intent.getIntExtra(TargetProfile.WIDTH, 0) == 1080 && intent.getIntExtra(TargetProfile.HEIGHT, 0) == 1440, "Camera crop and rotation recommendation");
        EditorState state = new EditorState(); state.ratio = "3:4"; state.fit(1600, 900);
        check(Math.abs(1600 * state.crop.width() / (900 * state.crop.height()) - .75f) < .001f, "Portrait recommendation crops without stretching");
        check(!state.customSize && state.width == 675 && state.height == 900, "Recommendation preserves available crop resolution");
        check(!CaptureRecommendation.attach(new android.content.Intent(), new Object()).hasExtra(TargetProfile.WIDTH), "Unknown capture has no invented recommendation");
        int[] configured = CaptureRecommendation.dimensions(new android.util.Size(1920, 1080), null, new android.util.Rational(3, 4), 90);
        check(configured[0] == 1080 && configured[1] == 1440, "Renamed CameraX configured ratio and rotation");
        int[] viewport = CaptureRecommendation.dimensions(new android.util.Size(1920, 1080), new android.graphics.Rect(0, 0, 1080, 1080), new android.util.Rational(3, 4), 90);
        check(viewport[0] == 1080 && viewport[1] == 1080, "Viewport crop takes precedence over configured ratio");
        report.append("Camera recommendation crop, rotation, portrait ratio and unsupported fallback passed\n");
    }

    private void cropZoom(Context context, StringBuilder report) throws Exception {
        Field imageField = CropView.class.getDeclaredField("image"); imageField.setAccessible(true);
        Field edgeField = CropView.class.getDeclaredField("expandAtEdge"); edgeField.setAccessible(true);
        runOnMainSync(() -> {
            Bitmap bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
            CropView view = new CropView(context);
            android.widget.FrameLayout parent = new android.widget.FrameLayout(context); parent.addView(view);
            parent.layout(0, 0, 400, 600); view.layout(0, 0, 400, 600);
            EditorState state = new EditorState(); state.ratio = "free"; state.fit(400, 400);
            view.setImage(bitmap, 400, 400, state, () -> state.refreshSize(400, 400));
            try {
                android.graphics.RectF full = new android.graphics.RectF((android.graphics.RectF) imageField.get(view));
                touch(view, android.view.MotionEvent.ACTION_DOWN, full.right, full.bottom);
                touch(view, android.view.MotionEvent.ACTION_MOVE, full.centerX(), full.centerY());
                check(Math.abs(((android.graphics.RectF) imageField.get(view)).width() - full.width()) < .1f, "No auto zoom during crop drag");
                touch(view, android.view.MotionEvent.ACTION_UP, full.centerX(), full.centerY());
                view.focusCrop(false);
                android.graphics.RectF focused = new android.graphics.RectF((android.graphics.RectF) imageField.get(view));
                check(Math.abs(state.crop.width() - .5f) < .01f && focused.width() > full.width() * 1.9f, "Released crop zooms to fill preview");
                check(state.width == 200 && state.height == 200, "Viewport zoom does not upscale output pixels");
                float right = focused.left + state.crop.right * focused.width();
                float bottom = focused.top + state.crop.bottom * focused.height();
                touch(view, android.view.MotionEvent.ACTION_DOWN, right, bottom);
                touch(view, android.view.MotionEvent.ACTION_MOVE, 399, 599);
                Runnable edge = (Runnable) edgeField.get(view);
                for (int i = 0; i < 100; i++) edge.run();
                check(state.crop.right > .99f && state.crop.bottom > .99f, "Outward edge drag can restore full image");
                touch(view, android.view.MotionEvent.ACTION_UP, 399, 599); view.focusCrop(false);
                check(state.width == 400 && state.height == 400, "Expanded crop restores original output size");
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            finally { parent.removeView(view); bitmap.recycle(); }
        });
        report.append("Crop release zoom, stable drag, reversible edge expansion and output pixel invariance passed\n");
    }
    private static void touch(CropView view, int action, float x, float y) {
        long time = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent event = android.view.MotionEvent.obtain(time, time, action, x, y, 0);
        view.onTouchEvent(event); event.recycle();
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
            galleryMemory(context, gallery, report);
            android.app.Instrumentation.ActivityMonitor monitor = addMonitor(EditorActivity.class.getName(), null, false);
            runOnMainSync(() -> grid.performItemClick(grid.getChildAt(0), 0, 0));
            editor = waitForMonitorWithTimeout(monitor, 8000); removeMonitor(monitor);
            check(editor != null && editor.getIntent().getData() != null, "Single tap opens selected image in editor");
            Field previewField = EditorActivity.class.getDeclaredField("preview"); previewField.setAccessible(true);
            deadline = android.os.SystemClock.uptimeMillis() + 8000;
            while (previewField.get(editor) == null && android.os.SystemClock.uptimeMillis() < deadline) { android.os.SystemClock.sleep(50); waitForIdleSync(); }
            check(previewField.get(editor) != null, "Selected image preview loaded");
            Field recommendationField = EditorActivity.class.getDeclaredField("recommended"); recommendationField.setAccessible(true);
            check(recommendationField.get(editor) == null, "Unknown target ratio hides recommendation");
            snapshot(context, editor, "editor-no-recommendation.png");
            recommendedUi(context, editor.getIntent().getData(), report);
            Field resetField = EditorActivity.class.getDeclaredField("reset"); resetField.setAccessible(true);
            final android.view.View reset = (android.view.View) resetField.get(editor);
            runOnMainSync(() -> reset.performClick());
            Field stateField = EditorActivity.class.getDeclaredField("state"); stateField.setAccessible(true);
            EditorState state = (EditorState) stateField.get(editor);
            check("original".equals(state.ratio) && !state.flipX && !state.flipY && !state.customSize && state.rotation == 0 && state.crop.equals(new android.graphics.RectF(0, 0, 1, 1)), "Reset restores original frame and dimensions");
            report.append("Gallery single-tap editor, selected image preview and UI reset passed\n");
        } finally {
            final android.app.Activity active = editor;
            runOnMainSync(() -> { if (active != null) active.finish(); gallery.finish(); }); waitForIdleSync();
        }
    }
    private void snapshot(Context context, android.app.Activity activity, String name) {
        android.os.SystemClock.sleep(450); waitForIdleSync();
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) return;
        try (FileOutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (java.io.IOException error) { throw new RuntimeException(error); }
        finally { bitmap.recycle(); }
    }

    private void recommendedUi(Context context, Uri source, StringBuilder report) throws Exception {
        android.app.Activity editor = startActivitySync(new android.content.Intent(context, EditorActivity.class).setData(source)
            .putExtra(TargetProfile.WIDTH, 1080).putExtra(TargetProfile.HEIGHT, 1440).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            Field preview = EditorActivity.class.getDeclaredField("preview"); preview.setAccessible(true);
            long deadline = android.os.SystemClock.uptimeMillis() + 8000;
            while (preview.get(editor) == null && android.os.SystemClock.uptimeMillis() < deadline) { android.os.SystemClock.sleep(50); waitForIdleSync(); }
            check(preview.get(editor) != null, "Recommendation preview loaded");
            snapshot(context, editor, "editor-recommendation.png");
            Field field = EditorActivity.class.getDeclaredField("recommended"); field.setAccessible(true);
            android.widget.Button button = (android.widget.Button) field.get(editor);
            check(button != null && "\u0e41\u0e19\u0e30\u0e19\u0e33".contentEquals(button.getText()), "Compact recommendation label");
            android.view.ViewGroup row = (android.view.ViewGroup) button.getParent();
            int index = row.indexOfChild(button);
            check(index >= 2 && row.getChildAt(index - 2) instanceof android.widget.Button
                && "\u0e2d\u0e34\u0e2a\u0e23\u0e30".contentEquals(((android.widget.Button) row.getChildAt(index - 2)).getText()), "Recommendation follows Free in ratio row");
            java.lang.reflect.Method apply = EditorActivity.class.getDeclaredMethod("applyRecommended", boolean.class); apply.setAccessible(true);
            runOnMainSync(() -> { try { apply.invoke(editor, true); } catch (Exception error) { throw new RuntimeException(error); } });
            Field state = EditorActivity.class.getDeclaredField("state"); state.setAccessible(true);
            check("3:4".equals(((EditorState) state.get(editor)).ratio), "Recommendation applies portrait ratio");
            runOnMainSync(() -> { try { apply.invoke(editor, false); } catch (Exception error) { throw new RuntimeException(error); } });
            check("4:3".equals(((EditorState) state.get(editor)).ratio), "Recommendation applies landscape ratio");
            Field rotateField = EditorActivity.class.getDeclaredField("rotate"); rotateField.setAccessible(true);
            android.widget.Button rotate = (android.widget.Button) rotateField.get(editor);
            runOnMainSync(rotate::performClick);
            check(((EditorState) state.get(editor)).rotation == 90 && "3:4".equals(((EditorState) state.get(editor)).ratio), "Rotation control rotates recommended crop orientation");
            report.append("Recommendation hidden without metadata, compact row placement and apply passed\n");
        } finally { runOnMainSync(editor::finish); waitForIdleSync(); }
    }

    private android.app.Activity openProfileGallery(android.app.Activity owner, String target) {
        long focusDeadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (!owner.hasWindowFocus() && android.os.SystemClock.uptimeMillis() < focusDeadline) { android.os.SystemClock.sleep(50); waitForIdleSync(); }
        android.app.Instrumentation.ActivityMonitor monitor = addMonitor(GalleryActivity.class.getName(), null, false);
        runOnMainSync(() -> owner.startActivityForResult(new android.content.Intent(owner, GalleryActivity.class).putExtra(TargetProfile.TARGET, target), 773));
        android.app.Activity result;
        long deadline = android.os.SystemClock.uptimeMillis() + 8000;
        do { result = waitForMonitorWithTimeout(monitor, 1000); }
        while ((result == null || !target.equals(result.getIntent().getStringExtra(TargetProfile.TARGET))) && android.os.SystemClock.uptimeMillis() < deadline);
        removeMonitor(monitor);
        check(result != null, "Profile gallery opened"); waitForIdleSync();
        return result;
    }
    private void waitForPhotos(android.widget.GridView grid) {
        long deadline = android.os.SystemClock.uptimeMillis() + 8000;
        while (grid.getChildCount() == 0 && android.os.SystemClock.uptimeMillis() < deadline) { android.os.SystemClock.sleep(50); waitForIdleSync(); }
        check(grid.getCount() > 0, "Profile gallery loaded"); waitForIdleSync();
    }
    private void galleryMemory(Context context, android.app.Activity owner, StringBuilder report) throws Exception {
        String first = "contract.gallery.a", second = "contract.gallery.b";
        android.app.Activity active = null;
        context.getSharedPreferences("gallery." + first, 0).edit().clear().commit();
        context.getSharedPreferences("gallery." + second, 0).edit().clear().commit();
        try {
            active = openProfileGallery(owner, first);
            check(first.equals(TargetProfile.target(active)), "Internal gallery forwards original target");
            android.widget.GridView grid = (android.widget.GridView) findView(active.getWindow().getDecorView(), android.widget.GridView.class);
            waitForPhotos(grid);
            Field albumsField = GalleryActivity.class.getDeclaredField("albums"); albumsField.setAccessible(true);
            java.util.Map<?, ?> albums = (java.util.Map<?, ?>) albumsField.get(active);
            check(!albums.isEmpty(), "Accessible album available");
            String album = (String) albums.keySet().iterator().next();
            java.lang.reflect.Method select = GalleryActivity.class.getDeclaredMethod("selectAlbum", String.class); select.setAccessible(true);
            final android.app.Activity selection = active;
            runOnMainSync(() -> { try { select.invoke(selection, album); } catch (Exception error) { throw new RuntimeException(error); } }); waitForIdleSync();
            int count = grid.getCount(); final int requested = count > 30 ? 12 : 0;
            runOnMainSync(() -> grid.setSelectionFromTop(requested, -7)); waitForIdleSync();
            int position = grid.getFirstVisiblePosition();
            int offset = grid.getChildAt(0).getTop();
            final android.app.Activity closing = active; runOnMainSync(closing::finish); waitForIdleSync(); active = null;
            check(context.getSharedPreferences("gallery." + first, 0).getString("album", "").equals(album), "Album persisted before choosing a photo");
            active = openProfileGallery(owner, second);
            Field albumField = GalleryActivity.class.getDeclaredField("albumId"); albumField.setAccessible(true);
            check(second.equals(TargetProfile.target(active)), "Second target identity");
            check("".equals(albumField.get(active)), "Other app starts with independent album");
            final android.app.Activity other = active; runOnMainSync(other::finish); waitForIdleSync(); active = null;
            active = openProfileGallery(owner, first);
            android.widget.GridView restored = (android.widget.GridView) findView(active.getWindow().getDecorView(), android.widget.GridView.class);
            waitForPhotos(restored);
            check(album.equals(albumField.get(active)), "Album restored after closing gallery");
            check(restored.getFirstVisiblePosition() == position && Math.abs(restored.getChildAt(0).getTop() - offset) <= 1, "Scroll position and offset restored");
            report.append("Per-app gallery identity, album persistence and scroll restoration passed\n");
        } finally {
            final android.app.Activity closing = active;
            if (closing != null) { runOnMainSync(closing::finish); waitForIdleSync(); }
            context.getSharedPreferences("gallery." + first, 0).edit().clear().commit();
            context.getSharedPreferences("gallery." + second, 0).edit().clear().commit();
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
        android.content.SharedPreferences targetPrefs = context.getSharedPreferences("editor-profile-contract", 0);
        targetPrefs.edit().clear().commit();
        EditorState inherited = EditorState.loadForTarget(targetPrefs, prefs);
        check(inherited.flipX && inherited.width == 20 && "1:1".equals(inherited.ratio), "New target retains legacy adjustments");
        inherited.ratio = "4:3"; inherited.flipX = false; inherited.save(targetPrefs);
        check("4:3".equals(EditorState.loadForTarget(targetPrefs, prefs).ratio), "Confirmed target adjustments survive reopening");
        check(EditorState.load(prefs).flipX, "Per-app edits preserve legacy settings");
        targetPrefs.edit().clear().commit();
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
        for (int degrees : new int[]{0, 90, 180, 270}) {
            EditorState rotated = new EditorState(); rotated.rotation = degrees; rotated.fit(80, 40);
            check(rotated.width == (degrees % 180 == 0 ? 80 : 40) && rotated.height == (degrees % 180 == 0 ? 40 : 80), "Rotation dimensions " + degrees);
            File rotatedFile = EditorRenderer.render(context, Uri.fromFile(source), rotated, 80, 40);
            Bitmap rotatedImage = android.graphics.BitmapFactory.decodeFile(rotatedFile.getAbsolutePath());
            int first = degrees % 180 == 0 ? rotatedImage.getPixel(rotated.width / 4, rotated.height / 2) : rotatedImage.getPixel(rotated.width / 2, rotated.height / 4);
            boolean redFirst = degrees == 0 || degrees == 90;
            check(redFirst ? android.graphics.Color.red(first) > 200 : android.graphics.Color.blue(first) > 200, "Rotation pixel orientation " + degrees);
            rotatedImage.recycle(); rotatedFile.delete();
        }
        EditorState rotatedCrop = new EditorState(); rotatedCrop.rotation = 90; rotatedCrop.ratio = "free";
        rotatedCrop.crop.set(0, 0, 1, .5f); rotatedCrop.refreshSize(80, 40);
        File croppedFile = EditorRenderer.render(context, Uri.fromFile(source), rotatedCrop, 80, 40);
        Bitmap croppedImage = android.graphics.BitmapFactory.decodeFile(croppedFile.getAbsolutePath());
        check(croppedImage.getWidth() == 40 && croppedImage.getHeight() == 40 && android.graphics.Color.red(croppedImage.getPixel(20, 20)) > 200, "Crop coordinates follow rotated image");
        croppedImage.recycle(); croppedFile.delete();
        EditorState rotation = new EditorState(); rotation.fit(80, 40); rotation.rotateClockwise(80, 40);
        rotation.save(prefs);
        check(EditorState.load(prefs).rotation == 90 && EditorState.restore(rotation.bundle()).rotation == 90, "Rotation persisted and restored");
        rotation.ratio = "3:4"; rotation.fit(80, 40); rotation.rotateClockwise(80, 40);
        check("4:3".equals(rotation.ratio) && rotation.rotation == 180, "Rotation swaps configured crop orientation");
        source.delete(); rendered.delete(); prefs.edit().clear().commit();
        report.append("Editor original ratio, saved settings, unconfirmed edits, mirror/crop/resize, provider and invalid size passed\n");
    }

    private void imageKeeper(Context context, Uri source, StringBuilder report) throws Exception {
        ClassLoader loader = loader(context, "com.branchphotovault");
        check(DexCaptureScanner.find(context.getPackageManager().getApplicationInfo("com.branchphotovault", 0))
            .contains("androidx.camera.core.ImageCapture"), "Image Keeper discovery");
        File destination = new File(context.getCacheDir(), "contract-keeper.jpg");
        resolutionContract(loader.loadClass("androidx.camera.core.ImageCapture"), report);
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
        resolutionContract(loader.loadClass("y.f0"), report);
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
        resolutionContract(loader.loadClass("z.Z"), report);
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

    private void resolutionContract(Class<?> capture, StringBuilder report) {
        boolean found = false;
        for (java.lang.reflect.Method getter : capture.getMethods()) {
            if (getter.getParameterCount() != 0) continue;
            boolean size = false, rect = false, rotation = false;
            for (Field field : getter.getReturnType().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                size |= field.getType() == android.util.Size.class;
                rect |= field.getType() == android.graphics.Rect.class;
                rotation |= field.getType() == int.class;
            }
            for (java.lang.reflect.Method value : getter.getReturnType().getDeclaredMethods()) {
                if (value.getParameterCount() != 0 || java.lang.reflect.Modifier.isStatic(value.getModifiers())) continue;
                size |= value.getReturnType() == android.util.Size.class;
                rect |= value.getReturnType() == android.graphics.Rect.class;
                rotation |= value.getReturnType() == int.class;
            }
            if (size && rect && rotation) { report.append(capture.getName() + " resolution getter: " + getter.getName() + "\n"); found = true; }
        }
        if (!found) {
            boolean ghost = capture.getName().equals("y.f0");
            boolean nera = capture.getName().equals("z.Z");
            check(ghost || nera, "Known renamed resolution contract");
            java.util.Map<String, Field> fields = new java.util.HashMap<>();
            java.util.Map<String, java.lang.reflect.Method> methods = new java.util.HashMap<>();
            for (Class<?> type = capture; type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) fields.putIfAbsent(field.getName(), field);
                for (java.lang.reflect.Method method : type.getDeclaredMethods()) methods.putIfAbsent(method.getName(), method);
            }
            check(fields.get(ghost ? "i" : "k").getType() == android.graphics.Rect.class, "Viewport crop field contract");
            check(fields.get(ghost ? "q" : "u").getType() == android.util.Rational.class, "Capture aspect ratio field contract");
            java.lang.reflect.Method rotation = methods.get(ghost ? "g" : "j");
            check(rotation.getReturnType() == int.class && rotation.getParameterTypes().length == 2 && rotation.getParameterTypes()[1] == boolean.class, "Relative rotation contract");
            check(methods.get(ghost ? "b" : "e").getParameterTypes().length == 0, "Bound camera getter contract");
            if (ghost) {
                Field stream = fields.get("g");
                try { check(stream.getType().getDeclaredField("a").getType() == android.util.Size.class, "Stream resolution field contract"); }
                catch (NoSuchFieldException error) { throw new AssertionError(error); }
            } else check(methods.get("d").getReturnType() == android.util.Size.class, "Attached resolution getter contract");
            report.append(capture.getName() + " renamed viewport, stream and rotation contracts passed\n");
        }
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

package com.phantom.p2cx;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.OutputStream;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CameraHook implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam app) {
        if (app.packageName.equals("com.phantom.p2cx") || app.packageName.equals("android")) return;
        RuntimeState state = new RuntimeState();
        ActivityResults results = new ActivityResults();
        results.install();
        new CameraXHook(state, app.packageName, results).install(app.classLoader);
        XposedBridge.hookAllMethods(Instrumentation.class, "execStartActivity", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Activity owner = null;
                int intentIndex = -1;
                int requestIndex = -1;
                for (int index = 0; index < param.args.length; index++) {
                    if (param.args[index] instanceof Activity) owner = (Activity) param.args[index];
                    if (param.args[index] instanceof Intent) intentIndex = index;
                    if (intentIndex >= 0 && index > intentIndex && param.args[index] instanceof Integer && requestIndex < 0) requestIndex = index;
                }
                if (owner == null || intentIndex < 0 || requestIndex < 0) return;
                Intent original = (Intent) param.args[intentIndex];
                int originalRequest = (int) param.args[requestIndex];
                if (!MediaStore.ACTION_IMAGE_CAPTURE.equals(original.getAction()) || originalRequest < 0 || !state.enabled()) return;
                Activity caller = owner;
                Uri output = original.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
                int code = results.register(owner, selection -> {
                    Uri source = selection.image();
                    if (source == null || !state.enabled()) {
                        results.forward(selection, originalRequest, Activity.RESULT_CANCELED, null);
                        return;
                    }
                    new Thread(() -> {
                        Bitmap bitmap = null;
                        try {
                            boolean edited = output != null && "com.phantom.p2cx.images".equals(source.getAuthority());
                            if (!edited) bitmap = Images.decode(caller, source, output == null ? 256 : 4096);
                            if (!state.enabled()) throw new java.io.IOException("P2C_X switched off");
                            Intent response = new Intent();
                            if (output == null) {
                                response.putExtra("data", bitmap);
                                bitmap = null;
                            } else {
                                try (OutputStream destination = caller.getContentResolver().openOutputStream(output, "wt")) {
                                    if (destination == null) throw new java.io.IOException("Cannot open capture output");
                                    if (edited) CaptureOutput.copy(caller, source, destination);
                                    else Images.jpeg(bitmap, destination);
                                }
                            }
                            results.forward(selection, originalRequest, Activity.RESULT_OK, response);
                            XposedBridge.log("P2C_X Camera Intent result delivered: " + app.packageName);
                        } catch (Exception | OutOfMemoryError error) {
                            XposedBridge.log("P2C_X Camera Intent image: " + error);
                            results.forward(selection, originalRequest, Activity.RESULT_CANCELED, null);
                        } finally { if (bitmap != null) bitmap.recycle(); }
                    }, "P2CX-intent").start();
                });
                param.args[requestIndex] = code;
                param.args[intentIndex] = ActivityResults.picker();
                XposedBridge.log("P2C_X Camera Intent intercepted: " + app.packageName);
            }
        });
    }
}

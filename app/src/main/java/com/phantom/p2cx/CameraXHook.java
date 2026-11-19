package com.phantom.p2cx;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

final class CameraXHook {
    private final RuntimeState state;
    private final String packageName;
    private final ActivityResults results;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean();
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private final java.util.Set<String> installed = new java.util.HashSet<>();
    private final AtomicBoolean scanning = new AtomicBoolean();

    CameraXHook(RuntimeState state, String packageName, ActivityResults results) {
        this.state = state;
        this.packageName = packageName;
        this.results = results;
    }

    void install(ClassLoader loader) {
        Hooks.all(Activity.class, "onResume", new Hooks.Callback() {
            @Override protected void after(Hooks.Call param) {
                Activity foreground = (Activity) param.thisObject;
                activity = new WeakReference<>(foreground);
                installCapture(foreground.getClassLoader());
                discover(foreground, foreground.getClassLoader());
            }
        });
        Hooks.exact(Application.class, "attach", Context.class, new Hooks.Callback() {
            @Override protected void after(Hooks.Call param) {
                installCapture(loader);
                discover((Context) param.args[0], loader);
            }
        });
    }

    private synchronized void installCapture(ClassLoader loader) {
        installClass("androidx.camera.core.ImageCapture", loader);
    }

    private void discover(Context context, ClassLoader loader) {
        if (!scanning.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                for (String name : DexCaptureScanner.find(context.getApplicationInfo())) installClass(name, loader);
            } catch (Throwable error) { Hooks.log("P2C_X CameraX discovery: " + error); }
        }, "P2CX-discovery").start();
    }

    private synchronized void installClass(String name, ClassLoader loader) {
        if (installed.contains(name)) return;
        Class<?> capture = Hooks.find(name, loader);
        if (capture == null) return;
        for (Method method : capture.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getReturnType() != void.class || types.length != 3 || types[1] != Executor.class) continue;
            if (name.equals("androidx.camera.core.ImageCapture") && !method.getName().equals("takePicture")) continue;
            Hooks.method(method, new Hooks.Callback() {
                @Override protected void before(Hooks.Call param) {
                    intercept(param);
                }
            });
            installed.add(name);
            Hooks.log("P2C_X CameraX ready: " + packageName + " " + method);
        }
    }

    private void intercept(Hooks.Call param) {
        if (!state.enabled()) return;
        Activity owner = activity.get();
        if (owner == null || owner.isFinishing() || owner.isDestroyed()) return;
        try {
            CaptureOutput output = new CaptureOutput(param.args[0]);
            CaptureCallback callback = CaptureCallback.create(param.args[2], output);
            Executor executor = (Executor) param.args[1];
            // Suppress repeated shutter events while the selected image is pending.
            if (!busy.compareAndSet(false, true)) {
                param.setResult(null);
                executor.execute(() -> {
                    try { callback.failure("An image selection is already pending"); }
                    catch (Exception error) { Hooks.log(error); }
                });
                return;
            }
            param.setResult(null);
            Context context = owner.getApplicationContext();
            Pending pending = new Pending(context, output, callback, executor);
            main.post(() -> pending.open(owner));
        } catch (Exception error) {
            Hooks.log("P2C_X unsupported CameraX contract: " + error);
        }
    }

    private final class Pending {
        private final Context context;
        private final CaptureOutput output;
        private final CaptureCallback callback;
        private final Executor executor;
        private final AtomicBoolean answered = new AtomicBoolean();
        private int requestCode = -1;
        private final Runnable timeout = () -> cancel("Image selection timed out");

        Pending(Context context, CaptureOutput output, CaptureCallback callback, Executor executor) {
            this.context = context;
            this.output = output;
            this.callback = callback;
            this.executor = executor;
        }

        void open(Activity owner) {
            if (owner.isFinishing() || owner.isDestroyed()) { cancel("Camera activity closed"); return; }
            try {
                requestCode = results.open(owner, selection -> {
                    if (!state.enabled()) {
                        cancel("Image selection cancelled");
                        return;
                    }
                    Uri image = selection.image();
                    if (image == null) { cancel("No selected image"); return; }
                    if (!answered.compareAndSet(false, true)) return;
                    main.removeCallbacks(timeout);
                    new Thread(() -> {
                        try {
                            if (!state.enabled()) throw new java.io.IOException("P2C_X switched off");
                            Uri saved = output.write(context, image, state::enabled);
                            deliver("image saved", () -> callback.success(saved));
                        } catch (Exception | OutOfMemoryError error) {
                            Hooks.log("P2C_X image write: " + error);
                            deliver("image write error", () -> callback.failure("Cannot save selected image: " + error.getMessage()));
                        }
                    }, "P2CX-deliver").start();
                });
                main.postDelayed(timeout, 600000);
                Hooks.log("P2C_X image selection opened: " + packageName);
            } catch (RuntimeException error) { cancel("Cannot open P2C_X picker"); }
        }

        private void cancel(String message) {
            if (!answered.compareAndSet(false, true)) return;
            results.forget(requestCode);
            main.removeCallbacks(timeout);
            deliver("cancelled", () -> callback.failure(message));
        }

        private void deliver(String outcome, Completion completion) {
            Runnable action = () -> {
                try {
                    completion.run();
                    Hooks.log("P2C_X " + outcome + ": " + packageName);
                } catch (Throwable error) { Hooks.log("P2C_X callback failed: " + error); }
                finally { busy.set(false); }
            };
            try { executor.execute(action); }
            catch (RuntimeException rejected) { main.post(action); }
        }
    }

    private interface Completion { void run() throws Exception; }
}

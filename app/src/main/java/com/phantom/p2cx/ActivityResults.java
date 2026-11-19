package com.phantom.p2cx;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Member;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Routes only the requests registered by this module through Android's result dispatcher. */
final class ActivityResults {
    private final ConcurrentHashMap<Integer, Request> requests = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(0x6200);
    private final Handler main = new Handler(Looper.getMainLooper());

    interface Callback { void accept(Selection result); }

    private static final class Request {
        final int task;
        final Callback callback;
        Request(Activity owner, Callback callback) { task = owner.getTaskId(); this.callback = callback; }
    }

    static final class Selection {
        final Activity activity;
        final Member dispatcher;
        final Object[] arguments;
        final int requestIndex;
        final int resultIndex;
        final int dataIndex;

        Selection(Hooks.Call param, int requestIndex, int resultIndex, int dataIndex) {
            activity = (Activity) param.thisObject;
            dispatcher = param.method;
            arguments = param.args.clone();
            this.requestIndex = requestIndex;
            this.resultIndex = resultIndex;
            this.dataIndex = dataIndex;
        }

        Uri image() {
            Intent data = (Intent) arguments[dataIndex];
            return (int) arguments[resultIndex] == Activity.RESULT_OK && data != null ? data.getData() : null;
        }
    }

    void install() {
        Hooks.all(Activity.class, "dispatchActivityResult", new Hooks.Callback() {
            @Override protected void before(Hooks.Call param) {
                Class<?>[] types = ((java.lang.reflect.Method) param.method).getParameterTypes();
                int requestIndex = -1;
                int resultIndex = -1;
                int dataIndex = -1;
                for (int index = 0; index < types.length; index++) {
                    if (types[index] == int.class && requestIndex < 0) requestIndex = index;
                    else if (types[index] == int.class && resultIndex < 0) resultIndex = index;
                    if (types[index] == Intent.class) dataIndex = index;
                }
                if (requestIndex < 0 || resultIndex < 0 || dataIndex < 0) return;
                int code = (int) param.args[requestIndex];
                Request request = requests.get(code);
                if (request == null || request.task != ((Activity) param.thisObject).getTaskId()) return;
                if (!requests.remove(code, request)) return;
                param.setResult(null);
                request.callback.accept(new Selection(param, requestIndex, resultIndex, dataIndex));
            }
        });
    }

    int register(Activity owner, Callback callback) {
        int code;
        do { code = 0x6000 + Math.floorMod(sequence.getAndIncrement(), 0x1fff); }
        while (requests.putIfAbsent(code, new Request(owner, callback)) != null);
        return code;
    }

    void forget(int code) { requests.remove(code); }

    int open(Activity owner, Callback callback) {
        int code = register(owner, callback);
        try { owner.startActivityForResult(picker(), code); }
        catch (RuntimeException error) { forget(code); throw error; }
        return code;
    }

    static Intent picker() {
        return new Intent().setClassName("com.phantom.p2cx", "com.phantom.p2cx.GalleryActivity");
    }

    void forward(Selection result, int requestCode, int resultCode, Intent data) {
        Object[] arguments = result.arguments.clone();
        arguments[result.requestIndex] = requestCode;
        arguments[result.resultIndex] = resultCode;
        arguments[result.dataIndex] = data;
        main.post(() -> {
            if (result.activity.isDestroyed() || result.activity.isFinishing()) return;
            try { Hooks.original(result.dispatcher, result.activity, arguments); }
            catch (Throwable error) { Hooks.log("P2C_X activity result: " + error); }
        });
    }
}

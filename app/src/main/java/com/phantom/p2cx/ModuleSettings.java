package com.phantom.p2cx;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import java.util.concurrent.CopyOnWriteArraySet;

/** Private local values are mirrored to the framework on connection and each edit. */
final class ModuleSettings {
    private static XposedService service;
    private static boolean ready;
    private static String connection = "Waiting for LSPosed Modern service";
    private static final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private ModuleSettings() { }
    private static SharedPreferences preferences(Context context) { return context.getSharedPreferences("state", Context.MODE_PRIVATE); }
    static synchronized boolean shared(Context context) { return ready; }
    static synchronized String status() { return connection; }
    static void observe(Runnable listener) { listeners.add(listener); main.post(listener); }
    static void unobserve(Runnable listener) { listeners.remove(listener); }
    private static void changed() { for (Runnable listener : listeners) main.post(listener); }
    static synchronized void connected(Context context, XposedService value) {
        service = value; sync(context); changed();
    }
    static synchronized void disconnected(XposedService value) {
        if (service == value) { service = null; ready = false; connection = "LSPosed service disconnected"; changed(); }
    }
    static synchronized boolean sync(Context context) {
        if (service == null) { ready = false; return false; }
        try {
            SharedPreferences local = preferences(context);
            boolean committed = service.getRemotePreferences("state").edit()
                .putBoolean("configured", true)
                .putBoolean("license_allowed", RemoteAccess.allowed(context))
                .putBoolean("enabled", local.getBoolean("enabled", true))
                .putBoolean("start_on_boot", local.getBoolean("start_on_boot", true)).commit();
            if (!committed) throw new IllegalStateException("Remote settings commit failed");
            ready = true; connection = "LSPosed Modern API " + service.getApiVersion() + " / settings synced";
            android.util.Log.i("P2C_X", "Remote settings synced: enabled=" + local.getBoolean("enabled", true));
            return true;
        } catch (RuntimeException error) {
            ready = false; connection = "Settings sync failed; reopen P2C_X to retry";
            android.util.Log.e("P2C_X", connection, error); return false;
        }
    }
    static synchronized void accessChanged(Context context) { sync(context); changed(); }
    static boolean enabled(Context context) { return preferences(context).getBoolean("enabled", true); }
    static boolean startOnBoot(Context context) { return preferences(context).getBoolean("start_on_boot", true); }
    static synchronized void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("enabled", enabled).commit(); sync(context); changed();
    }
    static synchronized void setStartOnBoot(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("start_on_boot", enabled).commit(); sync(context); changed();
    }
    static synchronized void initialize(Context context) {
        SharedPreferences prefs = preferences(context); SharedPreferences.Editor edit = prefs.edit();
        if (!prefs.contains("enabled")) edit.putBoolean("enabled", true);
        if (!prefs.contains("start_on_boot")) edit.putBoolean("start_on_boot", true);
        edit.commit();
    }
}

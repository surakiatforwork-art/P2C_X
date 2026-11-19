package com.phantom.p2cx;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores only the module's public on/off switch. LSPosed manages its shared directory. */
final class ModuleSettings {
    private ModuleSettings() { }

    @SuppressWarnings("deprecation")
    private static SharedPreferences preferences(Context context) {
        try { return context.getSharedPreferences("state", Context.MODE_WORLD_READABLE); }
        catch (SecurityException unavailable) { return context.getSharedPreferences("state", Context.MODE_PRIVATE); }
    }

    @SuppressWarnings("deprecation")
    static boolean shared(Context context) {
        try { context.getSharedPreferences("state", Context.MODE_WORLD_READABLE); return true; }
        catch (SecurityException unavailable) { return false; }
    }

    static boolean enabled(Context context) { return preferences(context).getBoolean("enabled", true); }

    static boolean startOnBoot(Context context) { return preferences(context).getBoolean("start_on_boot", true); }

    static void setStartOnBoot(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("start_on_boot", enabled).commit();
    }

    static void initialize(Context context) {
        SharedPreferences prefs = preferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        if (!prefs.contains("enabled")) edit.putBoolean("enabled", true);
        if (!prefs.contains("start_on_boot")) edit.putBoolean("start_on_boot", true);
        edit.commit();
    }

    static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("enabled", enabled).commit();
    }
}

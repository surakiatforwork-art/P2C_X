package com.phantom.p2cx;

import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;

/** Reads the framework's private remote preference group, with live updates. */
final class RuntimeState {
    private final XposedInterface framework;
    private SharedPreferences preferences;
    private boolean warned;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (prefs, key) -> {
        if ("enabled".equals(key)) Hooks.log("P2C_X remote switch changed: " + prefs.getBoolean("enabled", false));
    };
    RuntimeState(XposedInterface framework) { this.framework = framework; }
    synchronized boolean enabled() {
        try {
            if (preferences == null) {
                preferences = framework.getRemotePreferences("state");
                preferences.registerOnSharedPreferenceChangeListener(changes);
            }
            if (!preferences.getBoolean("configured", false)) {
                if (!warned) Hooks.log("P2C_X remote settings not initialized. Open P2C_X once.");
                warned = true; return false;
            }
            return preferences.getBoolean("enabled", true);
        } catch (RuntimeException unavailable) {
            preferences = null;
            if (!warned) Hooks.log("P2C_X remote settings unavailable: " + unavailable);
            warned = true; return false;
        }
    }
}

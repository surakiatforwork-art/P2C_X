package com.phantom.p2cx;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/** LSPosed transports settings without resolving a module component in the target app. */
final class RuntimeState {
    private XSharedPreferences preferences;
    private boolean warned;

    synchronized boolean enabled() {
        try {
            if (preferences == null) preferences = new XSharedPreferences("com.phantom.p2cx", "state");
            preferences.reload();
            if (!preferences.getFile().canRead()) {
                if (!warned) XposedBridge.log("P2C_X settings unavailable. Open P2C_X once after enabling its module.");
                warned = true;
                return false;
            }
            return preferences.getBoolean("enabled", true);
        } catch (Throwable error) {
            if (!warned) XposedBridge.log("P2C_X settings: " + error);
            warned = true;
            return false;
        }
    }
}

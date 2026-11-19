package com.phantom.p2cx;

import android.app.Application;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class P2cApp extends Application implements XposedServiceHelper.OnServiceListener {
    @Override public void onCreate() {
        super.onCreate();
        ModuleSettings.initialize(this);
        XposedServiceHelper.registerListener(this);
    }
    @Override public void onServiceBind(XposedService service) { ModuleSettings.connected(this, service); }
    @Override public void onServiceDied(XposedService service) { ModuleSettings.disconnected(service); }
}

package com.phantom.p2cx;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class PickerTile extends TileService {
    @Override public void onStartListening() { refresh(); }
    @Override public void onClick() {
        if (!RemoteAccess.allowed(this)) { refresh(); return; }
        ModuleSettings.setEnabled(this, !ModuleSettings.enabled(this));
        refresh();
    }
    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(!RemoteAccess.allowed(this) ? Tile.STATE_UNAVAILABLE : ModuleSettings.enabled(this) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("P2C_X");
        tile.updateTile();
    }
}

package com.phantom.p2cx;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
public final class MainActivity extends Activity {
 private TextView connectionStatus;
 private final Runnable updateConnection = () -> { if (connectionStatus != null) connectionStatus.setText(ModuleSettings.status()); };
 @Override protected void onStart() { super.onStart(); ModuleSettings.observe(updateConnection); ModuleSettings.sync(this); }
 @Override protected void onStop() { ModuleSettings.unobserve(updateConnection); super.onStop(); }
 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved); ModuleSettings.initialize(this);
  LinearLayout root = Ui.root(this);
  ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
  scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
  ImageView icon = new ImageView(this); icon.setImageResource(com.phantom.p2cx.R.mipmap.ic_launcher);
  LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(Ui.dp(this,100), Ui.dp(this,100));
  iconLayout.gravity=Gravity.CENTER; iconLayout.topMargin=Ui.dp(this,28); content.addView(icon,iconLayout);
  TextView title = Ui.text(this, "P2C_X", 26); title.setGravity(Gravity.CENTER); content.addView(title);
  TextView subtitle = Ui.text(this, "\u0e41\u0e01\u0e25\u0e40\u0e25\u0e2d\u0e23\u0e35\u0e48\u0e41\u0e25\u0e30\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e21\u0e37\u0e2d\u0e1b\u0e23\u0e31\u0e1a\u0e20\u0e32\u0e1e", 14); subtitle.setGravity(Gravity.CENTER); subtitle.setTextColor(Ui.MUTED); content.addView(subtitle);
  Switch enabled = new Switch(this); enabled.setTextColor(Ui.TEXT); enabled.setText("\u0e40\u0e25\u0e37\u0e2d\u0e01\u0e20\u0e32\u0e1e\u0e41\u0e17\u0e19\u0e01\u0e25\u0e49\u0e2d\u0e07");
  enabled.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,20)); enabled.setChecked(ModuleSettings.enabled(this));
  enabled.setOnCheckedChangeListener((v,value)->ModuleSettings.setEnabled(this,value)); content.addView(enabled);
  Switch boot = new Switch(this); boot.setTextColor(Ui.TEXT); boot.setText("\u0e40\u0e1b\u0e34\u0e14\u0e43\u0e0a\u0e49\u0e07\u0e32\u0e19\u0e40\u0e21\u0e37\u0e48\u0e2d\u0e40\u0e1b\u0e34\u0e14\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07");
  boot.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,24)); boot.setChecked(ModuleSettings.startOnBoot(this));
  boot.setOnCheckedChangeListener((v,value)->ModuleSettings.setStartOnBoot(this,value)); content.addView(boot);
  Button gallery = Ui.button(this, "\u0e40\u0e1b\u0e34\u0e14\u0e41\u0e01\u0e25\u0e40\u0e25\u0e2d\u0e23\u0e35\u0e48", true); gallery.setOnClickListener(v->startActivity(new Intent(this,GalleryActivity.class))); content.addView(gallery);
  TextView status = Ui.text(this, ModuleSettings.shared(this) ? "\u0e1e\u0e23\u0e49\u0e2d\u0e21\u0e41\u0e0a\u0e23\u0e4c\u0e2a\u0e27\u0e34\u0e15\u0e0a\u0e4c\u0e1c\u0e48\u0e32\u0e19 LSPosed" : "\u0e40\u0e1b\u0e34\u0e14\u0e42\u0e21\u0e14\u0e39\u0e25\u0e43\u0e19 LSPosed \u0e41\u0e25\u0e49\u0e27\u0e40\u0e1b\u0e34\u0e14 P2C_X \u0e2d\u0e35\u0e01\u0e04\u0e23\u0e31\u0e49\u0e07", 13);
  connectionStatus = status; status.setText(ModuleSettings.status()); status.setTextColor(Ui.ACCENT); status.setPadding(0,Ui.dp(this,20),0,Ui.dp(this,12)); content.addView(status);
  TextView help = Ui.text(this, "\u0e40\u0e25\u0e37\u0e2d\u0e01 scope \u0e43\u0e19 LSPosed \u0e41\u0e25\u0e49\u0e27\u0e40\u0e1b\u0e34\u0e14\u0e41\u0e2d\u0e1b\u0e40\u0e1b\u0e49\u0e32\u0e2b\u0e21\u0e32\u0e22\u0e43\u0e2b\u0e21\u0e48\n\u0e40\u0e1e\u0e34\u0e48\u0e21 Tile P2C_X \u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e40\u0e1b\u0e34\u0e14\u2013\u0e1b\u0e34\u0e14\u0e08\u0e32\u0e01\u0e41\u0e16\u0e1a\u0e15\u0e31\u0e49\u0e07\u0e04\u0e48\u0e32\u0e14\u0e48\u0e27\u0e19\n\n\u0e04\u0e48\u0e32\u0e1b\u0e23\u0e31\u0e1a\u0e20\u0e32\u0e1e\u0e08\u0e30\u0e16\u0e39\u0e01\u0e08\u0e14\u0e08\u0e33\u0e40\u0e21\u0e37\u0e48\u0e2d\u0e01\u0e14\u0e43\u0e0a\u0e49\u0e20\u0e32\u0e1e\u0e19\u0e35\u0e49\n\n\u0e40\u0e27\u0e2d\u0e23\u0e4c\u0e0a\u0e31\u0e19 0.6.0 modern test", 13);
  help.setTextColor(Ui.MUTED); content.addView(help);
 }
}

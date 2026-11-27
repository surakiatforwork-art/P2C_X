package com.phantom.p2cx;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.graphics.Typeface;
import android.widget.*;
public final class MainActivity extends Activity {
 private TextView connectionStatus, accessStatus;
 private EditText nicknameInput;
 private Button register, galleryButton;
 private final Runnable accessChanged = this::renderAccess;
 private final Runnable updateConnection = () -> { if (connectionStatus != null) connectionStatus.setText(ModuleSettings.status()); };
 @Override protected void onStart() { super.onStart(); ModuleSettings.observe(updateConnection); ModuleSettings.sync(this); RemoteAccess.observe(accessChanged); RemoteAccess.check(this, null); }
 @Override protected void onStop() { RemoteAccess.unobserve(accessChanged); ModuleSettings.unobserve(updateConnection); super.onStop(); }
 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved); ModuleSettings.initialize(this);
  LinearLayout root = Ui.root(this);
  ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
  scroll.setFillViewport(true);
  scroll.setClipToPadding(false);
  scroll.setVerticalScrollBarEnabled(false);
  content.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,20));
  scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
  LinearLayout header=Ui.row(this);
  header.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,18));
  content.addView(header);
  ImageView icon = new ImageView(this); icon.setImageResource(com.phantom.p2cx.R.mipmap.ic_launcher);
  LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(Ui.dp(this,58), Ui.dp(this,58));
  header.addView(icon,iconLayout);
  Ui.space(header,12);
  LinearLayout heading=new LinearLayout(this);
  heading.setOrientation(LinearLayout.VERTICAL);
  header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  TextView title = Ui.text(this, "P2C_X", 24);
  title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  heading.addView(title);
  TextView subtitle = Ui.text(this, "\u0e41\u0e01\u0e25\u0e40\u0e25\u0e2d\u0e23\u0e35\u0e48\u0e41\u0e25\u0e30\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e21\u0e37\u0e2d\u0e1b\u0e23\u0e31\u0e1a\u0e20\u0e32\u0e1e", 14); subtitle.setTextColor(Ui.MUTED); heading.addView(subtitle);
  LinearLayout account=section(content,"บัญชีและสิทธิ์");
  accessStatus = Ui.text(this, "", 14); accessStatus.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  accessStatus.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
  accessStatus.setBackground(Ui.background(this,Ui.SECONDARY,false)); item(account,accessStatus);
  nicknameInput = new EditText(this); nicknameInput.setHint("ชื่อเล่นของคุณ"); nicknameInput.setSingleLine(); nicknameInput.setTextColor(Ui.TEXT); nicknameInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)}); nicknameInput.setTextSize(14);
  nicknameInput.setMinHeight(Ui.dp(this,48));
  nicknameInput.setHintTextColor(Ui.MUTED);
  nicknameInput.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
  nicknameInput.setBackground(Ui.background(this,Ui.SURFACE,false));
  item(account,nicknameInput);
  register = Ui.button(this, "ยืนยันชื่อและลงทะเบียน", true); item(account,register);
  register.setOnClickListener(v -> {
   String name = nicknameInput.getText().toString().trim();
   if (name.isEmpty() || name.codePointCount(0,name.length()) > 40 || name.codePoints().anyMatch(Character::isISOControl)) { nicknameInput.setError("กรอกชื่อเล่น 1–40 ตัวอักษร"); return; }
   new android.app.AlertDialog.Builder(this).setTitle("ยืนยันชื่อเล่น: " + name).setMessage("ระบบจะอนุมัติการใช้งานให้อัตโนมัติหลังลงทะเบียน ตั้งชื่อได้ครั้งเดียว หากต้องการเปลี่ยนชื่อภายหลังให้ติดต่อแอดมิน")
    .setNegativeButton("ตรวจทาน",null).setPositiveButton("ยืนยัน",(dialog,which)->{
     register.setEnabled(false);
     RemoteAccess.submit(this,name,success->{ if (!isDestroyed()) { renderAccess(); if(!success) Toast.makeText(this,"ยังเชื่อมต่อไม่ได้ ระบบจะลองใหม่โดยอัตโนมัติ",Toast.LENGTH_LONG).show(); } }); renderAccess();
    }).show();
  });
  Button retry = Ui.button(this,"ตรวจสิทธิ์อีกครั้ง"); retry.setOnClickListener(v->{ retry.setEnabled(false); RemoteAccess.check(this,success->{ retry.setEnabled(true); if(!isDestroyed()) Toast.makeText(this,success?"อัปเดตสิทธิ์แล้ว":"ยังเชื่อมต่อไม่ได้ คงสิทธิ์ล่าสุดไว้",Toast.LENGTH_SHORT).show(); }); }); item(account,retry);
  TextView privacy = Ui.text(this,"ส่งเฉพาะชื่อเล่น รหัสเครื่อง รุ่น เวอร์ชัน เวลาติดต่อ และยอดเปิดแกลเลอรี่/ยืนยันภาพรายวัน ไม่มีการอัปโหลดภาพ",12); privacy.setTextColor(Ui.MUTED); privacy.setLineSpacing(Ui.dp(this,3),1f);
  Button code = Ui.button(this,"คัดลอกรหัสเครื่อง"); code.setOnClickListener(v->{try {
   String id=RemoteAccess.deviceCode(this); ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("P2C_X device",id));
   Toast.makeText(this,"คัดลอกรหัสแล้ว",Toast.LENGTH_SHORT).show();
  } catch(Exception error){Toast.makeText(this,"อ่านรหัสเครื่องไม่ได้",Toast.LENGTH_SHORT).show();}}); item(account,code); item(account,privacy);
  renderAccess();
  LinearLayout controls=section(content,"การตั้งค่า");
  Switch enabled = new Switch(this); enabled.setTextColor(Ui.TEXT); enabled.setText("\u0e40\u0e25\u0e37\u0e2d\u0e01\u0e20\u0e32\u0e1e\u0e41\u0e17\u0e19\u0e01\u0e25\u0e49\u0e2d\u0e07");
  enabled.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10)); enabled.setMinHeight(Ui.dp(this,48)); enabled.setChecked(ModuleSettings.enabled(this));
  enabled.setOnCheckedChangeListener((v,value)->ModuleSettings.setEnabled(this,value)); item(controls,enabled);
  Switch boot = new Switch(this); boot.setTextColor(Ui.TEXT); boot.setText("\u0e40\u0e1b\u0e34\u0e14\u0e43\u0e0a\u0e49\u0e07\u0e32\u0e19\u0e40\u0e21\u0e37\u0e48\u0e2d\u0e40\u0e1b\u0e34\u0e14\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07");
  boot.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10)); boot.setMinHeight(Ui.dp(this,48)); boot.setChecked(ModuleSettings.startOnBoot(this));
  boot.setOnCheckedChangeListener((v,value)->ModuleSettings.setStartOnBoot(this,value)); item(controls,boot);
  Button gallery = Ui.button(this, "\u0e40\u0e1b\u0e34\u0e14\u0e41\u0e01\u0e25\u0e40\u0e25\u0e2d\u0e23\u0e35\u0e48", true); galleryButton = gallery; gallery.setEnabled(RemoteAccess.allowed(this)); gallery.setOnClickListener(v->startActivity(new Intent(this,GalleryActivity.class))); item(controls,gallery);
  TextView status = Ui.text(this, ModuleSettings.shared(this) ? "\u0e1e\u0e23\u0e49\u0e2d\u0e21\u0e41\u0e0a\u0e23\u0e4c\u0e2a\u0e27\u0e34\u0e15\u0e0a\u0e4c\u0e1c\u0e48\u0e32\u0e19 LSPosed" : "\u0e40\u0e1b\u0e34\u0e14\u0e42\u0e21\u0e14\u0e39\u0e25\u0e43\u0e19 LSPosed \u0e41\u0e25\u0e49\u0e27\u0e40\u0e1b\u0e34\u0e14 P2C_X \u0e2d\u0e35\u0e01\u0e04\u0e23\u0e31\u0e49\u0e07", 13);
  connectionStatus = status; status.setText(ModuleSettings.status()); status.setTextColor(Ui.ACCENT); status.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
  status.setBackground(Ui.background(this,Ui.SECONDARY,false));
  LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);
  statusParams.topMargin=Ui.dp(this,8);
  controls.addView(status,1,statusParams);
  TextView help = Ui.text(this, "\u0e40\u0e25\u0e37\u0e2d\u0e01 scope \u0e43\u0e19 LSPosed \u0e41\u0e25\u0e49\u0e27\u0e40\u0e1b\u0e34\u0e14\u0e41\u0e2d\u0e1b\u0e40\u0e1b\u0e49\u0e32\u0e2b\u0e21\u0e32\u0e22\u0e43\u0e2b\u0e21\u0e48\n\u0e40\u0e1e\u0e34\u0e48\u0e21 Tile P2C_X \u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e40\u0e1b\u0e34\u0e14\u2013\u0e1b\u0e34\u0e14\u0e08\u0e32\u0e01\u0e41\u0e16\u0e1a\u0e15\u0e31\u0e49\u0e07\u0e04\u0e48\u0e32\u0e14\u0e48\u0e27\u0e19\n\n\u0e04\u0e48\u0e32\u0e1b\u0e23\u0e31\u0e1a\u0e20\u0e32\u0e1e\u0e08\u0e30\u0e16\u0e39\u0e01\u0e08\u0e14\u0e08\u0e33\u0e40\u0e21\u0e37\u0e48\u0e2d\u0e01\u0e14\u0e43\u0e0a\u0e49\u0e20\u0e32\u0e1e\u0e19\u0e35\u0e49\n\n\u0e40\u0e27\u0e2d\u0e23\u0e4c\u0e0a\u0e31\u0e19 " + BuildConfig.VERSION_NAME + "", 13);
  help.setTextColor(Ui.MUTED); help.setLineSpacing(Ui.dp(this,3),1f);
  LinearLayout about=section(content,"ข้อมูลแอปและอัปเดต");
  item(about,help);
  Button updates = Ui.button(this, "อัปเดตแอป");
  updates.setOnClickListener(v -> startActivity(new Intent(this, UpdateActivity.class))); item(about,updates);
 }
 private LinearLayout section(LinearLayout parent,String label) {
  LinearLayout panel=new LinearLayout(this);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,16));
  panel.setBackground(Ui.background(this,Ui.SURFACE,false));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
  params.bottomMargin=Ui.dp(this,14);
  parent.addView(panel,params);
  TextView labelView=Ui.text(this,label,16);
  labelView.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  panel.addView(labelView,new LinearLayout.LayoutParams(-1,-2));
  return panel;
 }
 private void item(LinearLayout parent,View view) {
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
  params.topMargin=Ui.dp(this,8);
  parent.addView(view,params);
 }
 private void renderAccess() {
  if (accessStatus == null) return;
  accessStatus.setText((RemoteAccess.named(this) ? RemoteAccess.nickname(this) + " · " : "") + RemoteAccess.status(this));
  boolean named = RemoteAccess.named(this); nicknameInput.setVisibility(named ? android.view.View.GONE : android.view.View.VISIBLE);
  register.setVisibility(named ? android.view.View.GONE : android.view.View.VISIBLE);
  register.setEnabled(RemoteAccess.configured());
  if (galleryButton != null) galleryButton.setEnabled(RemoteAccess.allowed(this));
 }

}

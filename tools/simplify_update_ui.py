from pathlib import Path
p=Path('app/src/main/java/com/phantom/p2cx/UpdateActivity.java')
s=p.read_text(encoding='utf-8')
def rep(a,b):
    global s
    assert s.count(a)==1,a
    s=s.replace(a,b,1)
rep('import android.widget.*;', '''import android.widget.*;
import android.view.View;
import android.view.Gravity;
import android.graphics.Typeface;''')
rep('    private Button check, download, install;', '''    private Button check, download, install;
    private TextView details;
    private ProgressBar progress;
    private boolean awaitingInstallPermission;
    private static final int INSTALL_PERMISSION_REQUEST = 431;
    private enum Stage { CHECK, CHECKING, AVAILABLE, DOWNLOADING, READY, VERIFYING }''')
a=s.index('        LinearLayout root = Ui.root(this);')
b=s.index('    private SharedPreferences prefs()',a)
s=s[:a]+'''        LinearLayout root = Ui.root(this);
        LinearLayout header = Ui.row(this);
        header.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 16));
        Button back = Ui.icon(this, "\\u2039", "ย้อนกลับ", false);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        Ui.space(header, 12);
        TextView title = Ui.text(this, "อัปเดตแอป", 22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        card.setBackground(Ui.background(this, Ui.SURFACE, false));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView app = Ui.text(this, "P2C_X", 20);
        app.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(app);
        TextView version = Ui.text(this, "เวอร์ชันปัจจุบัน " + BuildConfig.VERSION_NAME, 13);
        version.setTextColor(Ui.MUTED);
        card.addView(version);
        status = Ui.text(this, "ตรวจสอบว่ามีเวอร์ชันใหม่หรือไม่", 15);
        status.setLineSpacing(Ui.dp(this, 3), 1f);
        add(card, status, 18);
        details = Ui.text(this, "", 13);
        details.setTextColor(Ui.MUTED);
        details.setLineSpacing(Ui.dp(this, 3), 1f);
        details.setVisibility(View.GONE);
        add(card, details, 8);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        add(card, progress, 12);
        check = Ui.button(this, "ตรวจสอบการอัปเดต", true);
        check.setOnClickListener(v -> checkRelease()); add(card, check, 14);
        download = Ui.button(this, "ดาวน์โหลดอัปเดต", true);
        download.setOnClickListener(v -> download()); add(card, download, 14);
        install = Ui.button(this, "ติดตั้งอัปเดต", true);
        install.setOnClickListener(v -> verifyAndInstall()); add(card, install, 14);
        TextView note = Ui.text(this, "คุณเลือกอัปเดตได้เมื่อพร้อม แอปจะไม่ติดตั้งโดยอัตโนมัติ", 12);
        note.setTextColor(Ui.MUTED);
        note.setGravity(Gravity.CENTER);
        add(content, note, 16);
        stage(Stage.CHECK);
        downloadId = prefs().getLong("download", 0);
    }
    private void add(LinearLayout parent, View child, int gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(this, gap);
        parent.addView(child, params);
    }
    private void stage(Stage stage) {
        check.setVisibility(stage == Stage.CHECK || stage == Stage.CHECKING ? View.VISIBLE : View.GONE);
        check.setText(stage == Stage.CHECKING ? "กำลังตรวจสอบ…" : "ตรวจสอบการอัปเดต");
        check.setEnabled(stage == Stage.CHECK);
        download.setVisibility(stage == Stage.AVAILABLE || stage == Stage.DOWNLOADING ? View.VISIBLE : View.GONE);
        download.setText(stage == Stage.DOWNLOADING ? "กำลังดาวน์โหลด…" : "ดาวน์โหลดอัปเดต");
        download.setEnabled(stage == Stage.AVAILABLE);
        install.setVisibility(stage == Stage.READY || stage == Stage.VERIFYING ? View.VISIBLE : View.GONE);
        install.setText(stage == Stage.VERIFYING ? "กำลังตรวจสอบไฟล์…" : "ติดตั้งอัปเดต");
        install.setEnabled(stage == Stage.READY);
        boolean showProgress = stage == Stage.CHECKING || stage == Stage.DOWNLOADING || stage == Stage.VERIFYING;
        progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(showProgress);
    }
''' +s[b:]
rep('''        busy = true; check.setEnabled(false); download.setEnabled(false); asset = null;
        status.setText("กำลังตรวจสอบ…");''','''        busy = true; asset = null;
        details.setVisibility(View.GONE);
        stage(Stage.CHECKING);
        status.setText("กำลังตรวจสอบเวอร์ชันล่าสุด…");''')
rep('''            JSONObject found = null;''','''            JSONObject found = null;
            String releaseDetails = "";''')
rep('''if (code == 404) throw new Exception("ยังไม่มี Release เวอร์ชันปกติให้ดาวน์โหลด");''','''if (code == 404) throw new Exception("ยังไม่มีอัปเดตเวอร์ชันทางการ");''')
rep('''throw new Exception("Release มี APK หลายไฟล์ กรุณาเลือกไฟล์จากหน้า GitHub Releases");''','''throw new Exception("ยังไม่สามารถเลือกไฟล์อัปเดตได้ กรุณาติดต่อผู้พัฒนา");''')
a=s.index('                String tag = release.optString("tag_name");')
b=s.index('            } catch (Exception e)',a)
s=s[:a]+'''                String tag = release.optString("tag_name");
                if (found == null) message = "ยังไม่มีไฟล์อัปเดตที่พร้อมดาวน์โหลด";
                else if (!newer(tag, BuildConfig.VERSION_NAME)) {
                    found = null; message = "คุณกำลังใช้เวอร์ชันล่าสุดแล้ว";
                } else {
                    message = "มีอัปเดตใหม่ " + tag;
                    long bytes = found.optLong("size");
                    releaseDetails = (bytes > 0 ? String.format(java.util.Locale.ROOT, "ขนาดดาวน์โหลด %.1f MB", bytes / 1000000.0) + "\\n" : "")
                        + "ดาวน์โหลดและติดตั้งได้จากหน้านี้";
                }
''' +s[b:]
rep('''            JSONObject result = found; String text = message;
            runOnUiThread(() -> { if (isDestroyed()) return; busy = false; check.setEnabled(true); asset = result; download.setEnabled(result != null); status.setText(text); });''','''            JSONObject result = found; String text = message; String info = releaseDetails;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false; asset = result; status.setText(text);
                details.setText(info);
                details.setVisibility(result != null && !info.isEmpty() ? View.VISIBLE : View.GONE);
                stage(result == null ? Stage.CHECK : Stage.AVAILABLE);
            });''')
rep('''            prefs().edit().putLong("download", downloadId).apply();
            download.setEnabled(false); install.setEnabled(false); refreshDownload();''','''            prefs().edit().putLong("download", downloadId)
                .putString("digest", asset.optString("digest", ""))
                .putInt("source_version", BuildConfig.VERSION_CODE).apply();
            stage(Stage.DOWNLOADING); refreshDownload();''')
rep('''if (cursor == null || !cursor.moveToFirst()) { prefs().edit().remove("download").apply(); downloadId=0; return; }''','''if (cursor == null || !cursor.moveToFirst()) {
                prefs().edit().remove("download").remove("digest").apply(); downloadId=0;
                status.setText("ไม่พบไฟล์ดาวน์โหลด กรุณาตรวจสอบอีกครั้ง");
                stage(Stage.CHECK); return;
            }''')
a=s.index('            boolean active = state != DownloadManager.STATUS_SUCCESSFUL')
b=s.index('        } catch (Exception e) { status.setText("อ่านสถานะดาวน์โหลด',a)
s=s[:a]+'''            if (busy) return;
            if (state == DownloadManager.STATUS_SUCCESSFUL) {
                stage(Stage.READY);
                status.setText("ดาวน์โหลดสำเร็จ พร้อมติดตั้ง");
            } else if (state == DownloadManager.STATUS_FAILED) {
                manager().remove(downloadId);
                prefs().edit().remove("download").remove("digest").apply();
                downloadId = 0;
                status.setText("ดาวน์โหลดไม่สำเร็จ กรุณาลองใหม่");
                stage(asset != null ? Stage.AVAILABLE : Stage.CHECK);
            } else {
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                stage(Stage.DOWNLOADING);
                if (total > 0) {
                    int percent = (int)Math.min(100, done * 100 / total);
                    progress.setIndeterminate(false);
                    progress.setProgress(percent);
                    status.setText("กำลังดาวน์โหลด " + percent + "%");
                } else status.setText("กำลังเตรียมดาวน์โหลด…");
                handler.postDelayed(poll, 850);
            }
''' +s[b:]
rep('''        busy = true; install.setEnabled(false); status.setText("กำลังตรวจสอบไฟล์อัปเดต…");''','''        busy = true; stage(Stage.VERIFYING); status.setText("กำลังตรวจสอบไฟล์อัปเดต…");''')
rep('''                PackageManager pm = getPackageManager();''','''                String digest = prefs().getString("digest", "");
                if (digest != null && digest.startsWith("sha256:")) {
                    java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
                    try (java.io.InputStream stream = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[65536];
                        int count;
                        while ((count = stream.read(buffer)) != -1) sha.update(buffer, 0, count);
                    }
                    StringBuilder hex = new StringBuilder();
                    for (byte value : sha.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
                    if (!digest.substring(7).equalsIgnoreCase(hex.toString())) throw new Exception("ไฟล์อัปเดตไม่ผ่านการตรวจสอบ SHA-256");
                }
                PackageManager pm = getPackageManager();''')
rep('''            } catch (Exception e) { error = e.getMessage(); }''','''            } catch (Exception e) { error = e.getMessage() == null ? "ตรวจสอบไฟล์ไม่สำเร็จ" : e.getMessage(); }''')
rep('''                busy=false; install.setEnabled(true);
                if (result != null) { status.setText(result); return; }''','''                busy=false; stage(Stage.READY);
                if (result != null) {
                    manager().remove(downloadId);
                    prefs().edit().remove("download").remove("digest").apply();
                    downloadId = 0;
                    status.setText(result);
                    stage(asset != null ? Stage.AVAILABLE : Stage.CHECK);
                    return;
                }''')
rep('''                        status.setText("อนุญาตติดตั้งจาก P2C_X ในหน้าตั้งค่า แล้วกลับมากดติดตั้งอีกครั้ง");
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); return;''','''                        status.setText("อนุญาตติดตั้งจากแอปนี้เพื่อดำเนินการต่อ");
                        awaitingInstallPermission = true;
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())), INSTALL_PERMISSION_REQUEST); return;''')
rep('''    @Override protected void onStart() { super.onStart(); if (downloadId != 0) refreshDownload(); }''','''    @Override protected void onStart() {
        super.onStart();
        if (downloadId != 0 && prefs().getInt("source_version", BuildConfig.VERSION_CODE) < BuildConfig.VERSION_CODE) {
            manager().remove(downloadId);
            prefs().edit().remove("download").remove("digest").remove("source_version").apply();
            downloadId = 0;
        }
        if (downloadId != 0) refreshDownload();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INSTALL_PERMISSION_REQUEST && awaitingInstallPermission) {
            awaitingInstallPermission = false;
            if (getPackageManager().canRequestPackageInstalls() && downloadId != 0) verifyAndInstall();
            else status.setText("ยังไม่ได้อนุญาต สามารถกดติดตั้งอีกครั้งได้");
        }
    }''')
p.write_text(s,encoding='utf-8')

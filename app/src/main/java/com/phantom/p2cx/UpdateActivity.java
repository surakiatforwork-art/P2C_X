package com.phantom.p2cx;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import android.view.View;
import android.view.Gravity;
import android.graphics.Typeface;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Updates are checked and installed only on an explicit user action. */
public final class UpdateActivity extends Activity {
    private static final String REPO = "https://github.com/surakiatforwork-art/P2C_X";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button check, download, install;
    private TextView details;
    private ProgressBar progress;
    private boolean awaitingInstallPermission;
    private static final int INSTALL_PERMISSION_REQUEST = 431;
    private enum Stage { CHECK, CHECKING, AVAILABLE, DOWNLOADING, READY, VERIFYING }
    private JSONObject asset;
    private long downloadId;
    private boolean busy;
    private final Runnable poll = this::refreshDownload;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.root(this);
        LinearLayout header = Ui.row(this);
        header.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 16));
        Button back = Ui.icon(this, "\u2039", "ย้อนกลับ", false);
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
    private SharedPreferences prefs() { return getSharedPreferences("app_updates", MODE_PRIVATE); }
    @Override protected void onStart() {
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
    }
    @Override protected void onStop() { handler.removeCallbacks(poll); super.onStop(); }
    private void checkRelease() {
        if (busy) return;
        busy = true; asset = null;
        details.setVisibility(View.GONE);
        stage(Stage.CHECKING);
        status.setText("กำลังตรวจสอบเวอร์ชันล่าสุด…");
        new Thread(() -> {
            String message;
            JSONObject found = null;
            String releaseDetails = "";
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)new URL("https://api.github.com/repos/surakiatforwork-art/P2C_X/releases/latest").openConnection();
                connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "P2C_X-Updater");
                int code = connection.getResponseCode();
                if (code == 404) throw new Exception("ยังไม่มีอัปเดตเวอร์ชันทางการ");
                if (code != 200) throw new Exception(code == 403 || code == 429 ? "ตรวจสอบบ่อยเกินไป กรุณาลองใหม่ภายหลัง" : "เชื่อมต่อ GitHub ไม่สำเร็จ (" + code + ")");
                byte[] data;
                try (java.io.InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        if (output.size() > 1024 * 1024) throw new Exception("ข้อมูล Release ใหญ่เกินไป");
                    }
                    data = output.toByteArray();
                }
                if (data.length > 1024 * 1024) throw new Exception("ข้อมูล Release ใหญ่เกินไป");
                JSONObject release = new JSONObject(new String(data, StandardCharsets.UTF_8));
                JSONArray assets = release.getJSONArray("assets");
                for (int i=0; i<assets.length(); i++) {
                    JSONObject item = assets.getJSONObject(i);
                    String name = item.optString("name").toLowerCase(java.util.Locale.ROOT);
                    if (name.endsWith(".apk") && !name.contains("debug") && item.optString("browser_download_url").startsWith(REPO + "/releases/download/")) {
                        if (found != null) throw new Exception("ยังไม่สามารถเลือกไฟล์อัปเดตได้ กรุณาติดต่อผู้พัฒนา");
                        found = item;
                    }
                }
                String tag = release.optString("tag_name");
                if (found == null) message = "ยังไม่มีไฟล์อัปเดตที่พร้อมดาวน์โหลด";
                else if (!newer(tag, BuildConfig.VERSION_NAME)) {
                    found = null; message = "คุณกำลังใช้เวอร์ชันล่าสุดแล้ว";
                } else {
                    message = "มีอัปเดตใหม่ " + tag;
                    long bytes = found.optLong("size");
                    releaseDetails = (bytes > 0 ? String.format(java.util.Locale.ROOT, "ขนาดดาวน์โหลด %.1f MB", bytes / 1000000.0) + "\n" : "")
                        + "ดาวน์โหลดและติดตั้งได้จากหน้านี้";
                }
            } catch (Exception e) { found = null; message = e.getMessage() == null ? "ตรวจสอบไม่สำเร็จ กรุณาลองใหม่" : e.getMessage(); }
            finally { if (connection != null) connection.disconnect(); }
            JSONObject result = found; String text = message; String info = releaseDetails;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false; asset = result; status.setText(text);
                details.setText(info);
                details.setVisibility(result != null && !info.isEmpty() ? View.VISIBLE : View.GONE);
                stage(result == null ? Stage.CHECK : Stage.AVAILABLE);
            });
        }, "release-check").start();
    }
    static boolean newer(String candidate, String current) {
        try {
            String[] a = candidate.replaceFirst("^[vV]", "").split("[-+]", 2)[0].split("\\.");
            String[] b = current.replaceFirst("^[vV]", "").split("[-+]", 2)[0].split("\\.");
            for (int i=0; i<Math.max(a.length,b.length); i++) {
                int x = i<a.length ? Integer.parseInt(a[i]) : 0, y = i<b.length ? Integer.parseInt(b[i]) : 0;
                if (x != y) return x > y;
            }
            return current.contains("-") && !candidate.contains("-");
        } catch (Exception e) { return false; }
    }
    private DownloadManager manager() { return (DownloadManager)getSystemService(DOWNLOAD_SERVICE); }
    private void download() {
        if (asset == null || busy) return;
        try {
            if (downloadId != 0) manager().remove(downloadId);
            java.io.File destination = new java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk");
            if (destination.exists() && !destination.delete()) throw new Exception();
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(asset.getString("browser_download_url")))
                .setTitle("P2C_X อัปเดต").setMimeType("application/vnd.android.package-archive")
                .setDestinationUri(Uri.fromFile(destination)).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            downloadId = manager().enqueue(request);
            prefs().edit().putLong("download", downloadId)
                .putString("digest", asset.optString("digest", ""))
                .putInt("source_version", BuildConfig.VERSION_CODE).apply();
            stage(Stage.DOWNLOADING); refreshDownload();
        } catch (Exception e) { status.setText("เริ่มดาวน์โหลดไม่สำเร็จ กรุณาตรวจสอบพื้นที่ว่างแล้วลองใหม่"); }
    }
    private void refreshDownload() {
        handler.removeCallbacks(poll);
        if (downloadId == 0) return;
        try (Cursor cursor = manager().query(new DownloadManager.Query().setFilterById(downloadId))) {
            if (cursor == null || !cursor.moveToFirst()) {
                prefs().edit().remove("download").remove("digest").apply(); downloadId=0;
                status.setText("ไม่พบไฟล์ดาวน์โหลด กรุณาตรวจสอบอีกครั้ง");
                stage(Stage.CHECK); return;
            }
            int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (busy) return;
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
        } catch (Exception e) { status.setText("อ่านสถานะดาวน์โหลดไม่สำเร็จ ลองเปิดหน้านี้ใหม่"); }
    }
    @SuppressWarnings("deprecation") private void verifyAndInstall() {
        if (busy) return;
        busy = true; stage(Stage.VERIFYING); status.setText("กำลังตรวจสอบไฟล์อัปเดต…");
        new Thread(() -> {
            String error = null;
            try {
                java.io.File file = new java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk");
                String digest = prefs().getString("digest", "");
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
                PackageManager pm = getPackageManager();
                PackageInfo apk = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
                PackageInfo own = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
                if (apk == null || !getPackageName().equals(apk.packageName)) throw new Exception("ไฟล์อัปเดตไม่ใช่ P2C_X หรือไฟล์ไม่สมบูรณ์");
                long version = Build.VERSION.SDK_INT >= 28 ? apk.getLongVersionCode() : apk.versionCode;
                if (version <= BuildConfig.VERSION_CODE) throw new Exception("ไฟล์นี้ไม่ใช่เวอร์ชันใหม่กว่าแอปปัจจุบัน");
                if (apk.signatures == null || own.signatures == null || !Arrays.equals(apk.signatures, own.signatures)) throw new Exception("ลายเซ็น APK ไม่ตรงกับแอปที่ติดตั้ง กรุณาติดต่อผู้พัฒนา");
            } catch (Exception e) { error = e.getMessage() == null ? "ตรวจสอบไฟล์ไม่สำเร็จ" : e.getMessage(); }
            String result = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy=false; stage(Stage.READY);
                if (result != null) {
                    manager().remove(downloadId);
                    prefs().edit().remove("download").remove("digest").apply();
                    downloadId = 0;
                    status.setText(result);
                    stage(asset != null ? Stage.AVAILABLE : Stage.CHECK);
                    return;
                }
                try {
                    if (!getPackageManager().canRequestPackageInstalls()) {
                        status.setText("อนุญาตติดตั้งจากแอปนี้เพื่อดำเนินการต่อ");
                        awaitingInstallPermission = true;
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())), INSTALL_PERMISSION_REQUEST); return;
                    }
                    Uri uri = manager().getUriForDownloadedFile(downloadId);
                    if (uri == null) throw new Exception();
                    startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    status.setText("ยืนยันการติดตั้งในหน้าระบบ หากยกเลิกสามารถกดติดตั้งใหม่ได้");
                } catch (Exception e) { status.setText("เปิดตัวติดตั้งไม่ได้ กรุณาลองใหม่"); }
            });
        }, "verify-update").start();
    }
}

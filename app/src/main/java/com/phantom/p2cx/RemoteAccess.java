package com.phantom.p2cx;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/** All network activity belongs to the module app, never the injected target process. */
final class RemoteAccess {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<Runnable> observers = new CopyOnWriteArraySet<>();
    private static final List<java.util.function.Consumer<Boolean>> callbacks = new ArrayList<>();
    private static boolean checking;
    private static Runnable task;
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("access", 0); }
    static boolean configured() { return !BuildConfig.CONTROL_URL.isEmpty(); }
    static String nickname(Context c) { return prefs(c).getString("nickname", ""); }
    static boolean named(Context c) { return prefs(c).getBoolean("nickname_set", false); }
    static boolean allowed(Context c) { return !configured() || "approved".equals(prefs(c).getString("decision", "pending")); }
    static String status(Context c) {
        if (!configured()) return "ยังไม่ได้เชื่อมต่อระบบสิทธิ์";
        if (!named(c)) return "ตั้งชื่อเล่นเพื่อลงทะเบียนครั้งแรก";
        return switch (prefs(c).getString("decision", "pending")) {
            case "approved" -> "อนุมัติแล้ว · พร้อมใช้งาน";
            case "blocked" -> "เครื่องนี้ถูกระงับสิทธิ์ · ติดต่อแอดมิน";
            case "global" -> "ระบบปิดให้บริการชั่วคราว";
            default -> prefs(c).getString("token", "").isEmpty() ? "รอเชื่อมต่อเพื่อลงทะเบียนอัตโนมัติ" : "ลงทะเบียนแล้ว · กดตรวจสิทธิ์เพื่อรับสิทธิ์อัตโนมัติ";
        };
    }
    static void observe(Runnable callback) { observers.add(callback); main.post(callback); }
    static void unobserve(Runnable callback) { observers.remove(callback); }
    private static long interval() { return java.util.concurrent.ThreadLocalRandom.current().nextLong(720000, 1080001); }
    static void start(Context context) {
        if (!configured() || task != null) return;
        Context app = context.getApplicationContext();
        task = () -> check(app, null);
        main.post(task);
    }
    static void submit(Context c, String name, java.util.function.Consumer<Boolean> done) {
        name = name.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 40 || name.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("กรอกชื่อเล่น 1–40 ตัวอักษร");
        if (named(c)) throw new IllegalStateException("ยืนยันชื่อแล้ว กรุณาติดต่อแอดมินเพื่อเปลี่ยนชื่อ");
        prefs(c).edit().putString("nickname", name).putBoolean("nickname_set", true).commit();
        check(c, done);
    }
    static void check(Context context, java.util.function.Consumer<Boolean> done) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> check(context, done)); return; }
        if (!configured() || !named(context)) { if (done != null) done.accept(false); return; }
        long now = System.currentTimeMillis(), next = prefs(context).getLong("next", 0);
        if (done == null && !checking && next > now && next - now <= 1080000) { schedule(context); return; }
        if (done != null) callbacks.add(done);
        if (checking) return;
        checking = true;
        Context app = context.getApplicationContext();
        prefs(app).edit().putLong("next", now + interval()).apply();
        new Thread(() -> {
            JSONObject result = null;
            try { result = fetch(app); } catch (Exception ignored) { }
            JSONObject response = result;
            main.post(() -> {
                checking = false;
                boolean accepted = applyDecision(app, response);
                for (Runnable observer : observers) main.post(observer);
                List<java.util.function.Consumer<Boolean>> waiting = List.copyOf(callbacks); callbacks.clear();
                for (var callback : waiting) callback.accept(accepted);
                schedule(app);
            });
        }, "P2CX-access").start();
    }
    static boolean applyDecision(Context app, JSONObject response) {
        if (response == null) return false;
        String status = response.optString("deviceStatus"), nickname = response.optString("nickname");
        if (!(response.opt("enabled") instanceof Boolean enabled) || !(response.opt("allowed") instanceof Boolean allowed)
            || !List.of("approved", "pending", "blocked").contains(status) || nickname.isBlank()
            || allowed != (enabled && "approved".equals(status))) return false;
        prefs(app).edit().putString("decision", enabled ? status : "global").putString("nickname", nickname)
            .putLong("last_success", System.currentTimeMillis()).apply();
        ModuleSettings.accessChanged(app); return true;
    }

    private static void schedule(Context c) {
        if (task == null) return;
        main.removeCallbacks(task);
        long remaining = prefs(c).getLong("next", 0) - System.currentTimeMillis();
        main.postDelayed(task, Math.max(1000, Math.min(1080000, remaining)));
    }
    static String deviceCode(Context context) throws Exception {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isBlank()) throw new IOException("Device ID unavailable");
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(("P2C_X:" + id).getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : hash) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private static JSONObject fetch(Context c) throws Exception {
        String token = prefs(c).getString("token", "");
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean register = token.isEmpty();
            JSONObject request = new JSONObject().put("type", register ? "register" : "checkin").put("deviceId", deviceCode(c))
                .put("nickname", nickname(c)).put("version", BuildConfig.VERSION_NAME).put("model", Build.MODEL).put("usage", UsageCounters.snapshot(c));
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.CONTROL_URL).openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(8000); connection.setReadTimeout(22000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
            if (!register) connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setFixedLengthStreamingMode(body.length);
            try {
                try (OutputStream out = connection.getOutputStream()) { out.write(body); }
                int code = connection.getResponseCode();
                if (code == 401 && !register) { token = ""; prefs(c).edit().remove("token").apply(); continue; }
                if (code != 200) throw new IOException("Service unavailable");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[1024]; int read;
                    while ((read = in.read(buffer)) != -1) { if (out.size() + read > 16384) throw new IOException("Response too large"); out.write(buffer, 0, read); }
                }
                JSONObject response = new JSONObject(out.toString("UTF-8"));
                String status = response.optString("deviceStatus");
                if (!Boolean.TRUE.equals(response.opt("ok")) || !(response.opt("enabled") instanceof Boolean)
                    || !(response.opt("allowed") instanceof Boolean) || !List.of("approved", "pending", "blocked").contains(status)
                    || response.optString("nickname").isBlank()) throw new IOException("Invalid decision");
                boolean expected = response.getBoolean("enabled") && "approved".equals(status);
                if (expected != response.getBoolean("allowed")) throw new IOException("Inconsistent decision");
                if (register) { token = response.optString("token"); if (!token.matches("[a-f0-9]{64}")) throw new IOException("Invalid token"); prefs(c).edit().putString("token", token).apply(); }
                return response;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Register again later");
    }
}

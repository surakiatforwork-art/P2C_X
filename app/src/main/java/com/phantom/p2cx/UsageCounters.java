package com.phantom.p2cx;
import android.content.Context;
import org.json.*;
import java.time.*;
final class UsageCounters {
    static synchronized void record(Context c, String event) {
        String day = LocalDate.now(ZoneId.of("Asia/Bangkok")).toString();
        var prefs = c.getSharedPreferences("usage", 0); String key = day + "." + event;
        prefs.edit().putInt(key, Math.min(1000000, prefs.getInt(key, 0) + 1)).apply();
    }
    static synchronized JSONArray snapshot(Context c) throws JSONException {
        var prefs = c.getSharedPreferences("usage", 0); JSONArray days = new JSONArray();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bangkok"));
        for (int i = 0; i < 7; i++) {
            String day = today.minusDays(i).toString(); int opens = prefs.getInt(day + ".opens", 0), confirmed = prefs.getInt(day + ".confirmed", 0);
            if (opens != 0 || confirmed != 0) days.put(new JSONObject().put("day", day).put("opens", opens).put("confirmed", confirmed));
        }
        var edit = prefs.edit();
        for (String key : prefs.getAll().keySet()) if (key.length() >= 10 && key.substring(0, 10).compareTo(today.minusDays(7).toString()) < 0) edit.remove(key);
        edit.apply(); return days;
    }
}

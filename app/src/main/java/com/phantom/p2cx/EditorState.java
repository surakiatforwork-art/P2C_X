package com.phantom.p2cx;

import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Bundle;

/** Crop coordinates use the EXIF-oriented, user-rotated and mirrored image, normalized to 0..1. */
final class EditorState {
    String ratio = "original";
    int rotation;
    boolean flipX;
    boolean flipY;
    boolean locked = true;
    boolean customSize;
    int width;
    int height;
    final RectF crop = new RectF(0, 0, 1, 1);

    // Before profiles, confirmed adjustments were stored in the global editor file.
    // Keep those as the initial settings until this target confirms its own settings.
    static EditorState loadForTarget(SharedPreferences target, SharedPreferences legacy) {
        return load(target.contains("ratio") ? target : legacy);
    }

    static EditorState load(SharedPreferences prefs) {
        EditorState state = new EditorState();
        state.rotation = Math.floorMod(prefs.getInt("rotation", 0) / 90, 4) * 90;
        state.ratio = prefs.getString("ratio", "original");
        state.flipX = prefs.getBoolean("flipX", false);
        state.flipY = prefs.getBoolean("flipY", false);
        state.locked = prefs.getBoolean("locked", true);
        state.customSize = prefs.getBoolean("customSize", false);
        state.width = prefs.getInt("width", 0);
        state.height = prefs.getInt("height", 0);
        state.crop.set(prefs.getFloat("left", 0), prefs.getFloat("top", 0),
            prefs.getFloat("right", 1), prefs.getFloat("bottom", 1));
        return state;
    }

    void save(SharedPreferences prefs) {
        prefs.edit().putInt("rotation", rotation).putString("ratio", ratio).putBoolean("flipX", flipX).putBoolean("flipY", flipY)
            .putBoolean("locked", locked).putBoolean("customSize", customSize)
            .putInt("width", width).putInt("height", height)
            .putFloat("left", crop.left).putFloat("top", crop.top)
            .putFloat("right", crop.right).putFloat("bottom", crop.bottom).commit();
    }

    Bundle bundle() {
        Bundle state = new Bundle();
        state.putInt("rotation", rotation);
        state.putString("ratio", ratio);
        state.putBoolean("flipX", flipX);
        state.putBoolean("flipY", flipY);
        state.putBoolean("locked", locked);
        state.putBoolean("customSize", customSize);
        state.putInt("width", width);
        state.putInt("height", height);
        state.putFloatArray("crop", new float[]{crop.left, crop.top, crop.right, crop.bottom});
        return state;
    }

    static EditorState restore(Bundle bundle) {
        EditorState state = new EditorState();
        state.rotation = Math.floorMod(bundle.getInt("rotation", 0) / 90, 4) * 90;
        state.ratio = bundle.getString("ratio", "original");
        state.flipX = bundle.getBoolean("flipX");
        state.flipY = bundle.getBoolean("flipY");
        state.locked = bundle.getBoolean("locked", true);
        state.customSize = bundle.getBoolean("customSize");
        state.width = bundle.getInt("width");
        state.height = bundle.getInt("height");
        float[] crop = bundle.getFloatArray("crop");
        if (crop != null && crop.length == 4) state.crop.set(crop[0], crop[1], crop[2], crop[3]);
        return state;
    }

    int imageWidth(int width, int height) { return rotation % 180 == 0 ? width : height; }
    int imageHeight(int width, int height) { return rotation % 180 == 0 ? height : width; }

    void rotateClockwise(int sourceWidth, int sourceHeight) {
        rotation = (rotation + 90) % 360;
        crop.set(1 - crop.bottom, crop.left, 1 - crop.top, crop.right);
        if (ratio != null && ratio.contains(":")) { String[] parts = ratio.split(":"); ratio = parts[1] + ":" + parts[0]; }
        boolean previousFlipX = flipX; flipX = flipY; flipY = previousFlipX;
        int previousWidth = width; width = height; height = previousWidth;
        fit(sourceWidth, sourceHeight);
    }

    float aspect(int sourceWidth, int sourceHeight) {
        int originalWidth = sourceWidth;
        sourceWidth = imageWidth(originalWidth, sourceHeight);
        sourceHeight = imageHeight(originalWidth, sourceHeight);
        switch (ratio) {
            case "free": return 0;
            case "1:1": return 1;
            case "4:3": return 4f / 3;
            case "16:9": return 16f / 9;
            default:
                if (ratio != null && ratio.matches("[1-9][0-9]{0,4}:[1-9][0-9]{0,4}")) {
                    String[] parts = ratio.split(":");
                    return Integer.parseInt(parts[0]) / (float) Integer.parseInt(parts[1]);
                }
                ratio = "original"; return sourceWidth / (float) sourceHeight;
        }
    }

    void fit(int sourceWidth, int sourceHeight) {
        int originalWidth = sourceWidth, originalHeight = sourceHeight;
        sourceWidth = imageWidth(originalWidth, originalHeight);
        sourceHeight = imageHeight(originalWidth, originalHeight);
        float left = finite(crop.left, 0);
        float top = finite(crop.top, 0);
        float right = finite(crop.right, 1);
        float bottom = finite(crop.bottom, 1);
        float centerX = clamp((left + right) / 2, 0, 1);
        float centerY = clamp((top + bottom) / 2, 0, 1);
        float width = clamp(right - left, .02f, 1);
        float height = clamp(bottom - top, .02f, 1);
        float aspect = aspect(originalWidth, originalHeight);
        if (aspect > 0) {
            float normalizedRatio = aspect * sourceHeight / sourceWidth;
            height = width / normalizedRatio;
            if (height > 1) { height = 1; width = normalizedRatio; }
        }
        left = clamp(centerX - width / 2, 0, 1 - width);
        top = clamp(centerY - height / 2, 0, 1 - height);
        crop.set(left, top, left + width, top + height);
        refreshSize(originalWidth, originalHeight);
    }

    void refreshSize(int sourceWidth, int sourceHeight) {
        int originalWidth = sourceWidth;
        sourceWidth = imageWidth(originalWidth, sourceHeight);
        sourceHeight = imageHeight(originalWidth, sourceHeight);
        float aspect = sourceWidth * crop.width() / (sourceHeight * crop.height());
        if (!customSize || width <= 0 || height <= 0) {
            width = Math.max(1, Math.round(sourceWidth * crop.width()));
            height = Math.max(1, Math.round(sourceHeight * crop.height()));
        } else if (locked) {
            height = Math.max(1, Math.round(width / aspect));
        }
    }

    static float clamp(float value, float low, float high) { return Math.max(low, Math.min(value, high)); }
    private static float finite(float value, float fallback) { return Float.isNaN(value) || Float.isInfinite(value) ? fallback : value; }
}

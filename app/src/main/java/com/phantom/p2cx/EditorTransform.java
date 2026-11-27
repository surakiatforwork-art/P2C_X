package com.phantom.p2cx;

import android.graphics.*;

/** The same rotation/flip transform is used for preview and exported pixels. */
final class EditorTransform {
    static void draw(Canvas canvas, Bitmap bitmap, EditorState state, RectF destination, Paint paint) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int orientedWidth = state.imageWidth(width, height), orientedHeight = state.imageHeight(width, height);
        canvas.save();
        canvas.translate(destination.left, destination.top);
        canvas.scale(destination.width() / orientedWidth, destination.height() / orientedHeight);
        if (state.flipX) { canvas.translate(orientedWidth, 0); canvas.scale(-1, 1); }
        if (state.flipY) { canvas.translate(0, orientedHeight); canvas.scale(1, -1); }
        if (state.rotation == 90) { canvas.translate(height, 0); canvas.rotate(90); }
        else if (state.rotation == 180) { canvas.translate(width, height); canvas.rotate(180); }
        else if (state.rotation == 270) { canvas.translate(0, width); canvas.rotate(270); }
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();
    }
}

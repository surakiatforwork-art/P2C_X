package com.phantom.p2cx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class CropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF image = new RectF();
    private final RectF initial = new RectF();
    private Bitmap bitmap;
    private EditorState state;
    private int sourceWidth;
    private int sourceHeight;
    private int handle;
    private float downX;
    private float downY;
    private Runnable changed;

    CropView(Context context) {
        super(context);
        setContentDescription("\u0e01\u0e23\u0e2d\u0e1a\u0e04\u0e23\u0e2d\u0e1b \u0e25\u0e32\u0e01\u0e21\u0e38\u0e21\u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e1b\u0e23\u0e31\u0e1a\u0e02\u0e19\u0e32\u0e14 \u0e2b\u0e23\u0e37\u0e2d\u0e25\u0e32\u0e01\u0e14\u0e49\u0e32\u0e19\u0e43\u0e19\u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e22\u0e49\u0e32\u0e22\u0e01\u0e23\u0e2d\u0e1a");
        setMinimumHeight(Ui.dp(context, 160));
    }

    void setImage(Bitmap bitmap, int width, int height, EditorState state, Runnable changed) {
        this.bitmap = bitmap;
        sourceWidth = width;
        sourceHeight = height;
        this.state = state;
        this.changed = changed;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || state == null) return;
        float padding = Ui.dp(getContext(), 6);
        float scale = Math.min((getWidth() - 2 * padding) / bitmap.getWidth(),
            (getHeight() - 2 * padding) / bitmap.getHeight());
        if (scale <= 0) return;
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        image.set((getWidth() - width) / 2, (getHeight() - height) / 2,
            (getWidth() + width) / 2, (getHeight() + height) / 2);
        canvas.save();
        canvas.scale(state.flipX ? -1 : 1, state.flipY ? -1 : 1, image.centerX(), image.centerY());
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(bitmap, null, image, paint);
        canvas.restore();
        RectF crop = screenCrop();
        paint.setColor(0x55000000);
        canvas.drawRect(image.left, image.top, image.right, crop.top, paint);
        canvas.drawRect(image.left, crop.bottom, image.right, image.bottom, paint);
        canvas.drawRect(image.left, crop.top, crop.left, crop.bottom, paint);
        canvas.drawRect(crop.right, crop.top, image.right, crop.bottom, paint);
        paint.setColor(0x55ffffff);
        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        for (int index = 1; index < 3; index++) {
            float x = crop.left + crop.width() * index / 3;
            float y = crop.top + crop.height() * index / 3;
            canvas.drawLine(x, crop.top, x, crop.bottom, paint);
            canvas.drawLine(crop.left, y, crop.right, y, paint);
        }
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        canvas.drawRect(crop, paint);
        paint.setStrokeWidth(Ui.dp(getContext(), 4));
        float length = Math.min(Ui.dp(getContext(), 18), Math.min(crop.width(), crop.height()) / 3);
        corner(canvas, crop.left, crop.top, length, length);
        corner(canvas, crop.right, crop.top, -length, length);
        corner(canvas, crop.right, crop.bottom, -length, -length);
        corner(canvas, crop.left, crop.bottom, length, -length);
        paint.setStyle(Paint.Style.FILL);
    }

    private void corner(Canvas canvas, float x, float y, float dx, float dy) {
        canvas.drawLine(x, y, x + dx, y, paint);
        canvas.drawLine(x, y, x, y + dy, paint);
    }

    private RectF screenCrop() {
        return new RectF(image.left + state.crop.left * image.width(), image.top + state.crop.top * image.height(),
            image.left + state.crop.right * image.width(), image.top + state.crop.bottom * image.height());
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (state == null || bitmap == null || image.isEmpty() || !isEnabled()) return false;
        float x = (event.getX() - image.left) / image.width();
        float y = (event.getY() - image.top) / image.height();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            RectF crop = screenCrop();
            float radius = Ui.dp(getContext(), 30);
            float[][] corners = {{crop.left, crop.top}, {crop.right, crop.top}, {crop.right, crop.bottom}, {crop.left, crop.bottom}};
            handle = 0;
            for (int index = 0; index < corners.length; index++) {
                if (Math.hypot(event.getX() - corners[index][0], event.getY() - corners[index][1]) <= radius) { handle = index + 1; break; }
            }
            if (handle == 0 && !crop.contains(event.getX(), event.getY())) return false;
            downX = x;
            downY = y;
            initial.set(state.crop);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (handle == 0) {
                float dx = EditorState.clamp(x - downX, -initial.left, 1 - initial.right);
                float dy = EditorState.clamp(y - downY, -initial.top, 1 - initial.bottom);
                state.crop.set(initial);
                state.crop.offset(dx, dy);
            } else resize(x, y);
            if (changed != null) changed.run();
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
            performClick();
            return true;
        }
        return true;
    }

    private void resize(float x, float y) {
        boolean left = handle == 1 || handle == 4;
        boolean top = handle == 1 || handle == 2;
        float anchorX = left ? initial.right : initial.left;
        float anchorY = top ? initial.bottom : initial.top;
        float maxWidth = left ? anchorX : 1 - anchorX;
        float maxHeight = top ? anchorY : 1 - anchorY;
        float width = Math.max(0, (EditorState.clamp(x, 0, 1) - anchorX) * (left ? -1 : 1));
        float height = Math.max(0, (EditorState.clamp(y, 0, 1) - anchorY) * (top ? -1 : 1));
        float aspect = state.aspect(sourceWidth, sourceHeight);
        if (aspect > 0) {
            float ratio = aspect * sourceHeight / sourceWidth;
            float maximum = Math.min(maxWidth, maxHeight * ratio);
            width = EditorState.clamp(Math.max(width, height * ratio), Math.min(.02f, maximum), maximum);
            height = width / ratio;
        } else {
            width = EditorState.clamp(width, Math.min(.02f, maxWidth), maxWidth);
            height = EditorState.clamp(height, Math.min(.02f, maxHeight), maxHeight);
        }
        state.crop.set(left ? anchorX - width : anchorX, top ? anchorY - height : anchorY,
            left ? anchorX : anchorX + width, top ? anchorY : anchorY + height);
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}

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
    private boolean touching;
    private float pointerX, pointerY, startX, startY;
    private android.animation.ValueAnimator zoom;
    private final Runnable expandAtEdge = new Runnable() {
        public void run() {
            if (!touching || handle == 0 || !isEnabled() || !outwardAtEdge()) return;
            if (zoomOutStep()) {
                resize((pointerX - image.left) / image.width(), (pointerY - image.top) / image.height());
                if (changed != null) changed.run();
                invalidate(); postOnAnimation(this);
            }
        }
    };

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
        stopMotion(); image.setEmpty(); focusCrop(false);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || state == null) return;
        if (image.isEmpty()) focusCrop(false);
        if (image.isEmpty()) return;
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        EditorTransform.draw(canvas, bitmap, state, image, paint);
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
            if (zoom != null) { zoom.cancel(); zoom = null; }
            startX = pointerX = event.getX(); startY = pointerY = event.getY();
            RectF crop = screenCrop();
            float radius = Ui.dp(getContext(), 30);
            float[][] corners = {{crop.left, crop.top}, {crop.right, crop.top}, {crop.right, crop.bottom}, {crop.left, crop.bottom}};
            handle = 0;
            for (int index = 0; index < corners.length; index++) {
                if (Math.hypot(event.getX() - corners[index][0], event.getY() - corners[index][1]) <= radius) { handle = index + 1; break; }
            }
            if (handle == 0 && !crop.contains(event.getX(), event.getY())) return false;
            touching = true;
            downX = x;
            downY = y;
            initial.set(state.crop);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && touching) {
            pointerX = event.getX(); pointerY = event.getY();
            removeCallbacks(expandAtEdge);
            if (handle != 0 && outwardAtEdge()) postOnAnimation(expandAtEdge);
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
            touching = false; removeCallbacks(expandAtEdge);
            getParent().requestDisallowInterceptTouchEvent(false);
            focusCrop(true);
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
            float ratio = aspect * state.imageHeight(sourceWidth, sourceHeight) / state.imageWidth(sourceWidth, sourceHeight);
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

    void focusCrop(boolean animate) {
        if (touching || bitmap == null || state == null || getWidth() == 0 || getHeight() == 0) return;
        float margin = Ui.dp(getContext(), 24);
        int width = state.imageWidth(bitmap.getWidth(), bitmap.getHeight());
        int height = state.imageHeight(bitmap.getWidth(), bitmap.getHeight());
        float scale = Math.min((getWidth() - margin * 2) / (width * state.crop.width()),
            (getHeight() - margin * 2) / (height * state.crop.height()));
        if (scale <= 0) return;
        float w = width * scale, h = height * scale;
        RectF target = new RectF(getWidth() / 2f - state.crop.centerX() * w, getHeight() / 2f - state.crop.centerY() * h,
            getWidth() / 2f + (1 - state.crop.centerX()) * w, getHeight() / 2f + (1 - state.crop.centerY()) * h);
        if (zoom != null) { zoom.cancel(); zoom = null; }
        if (!animate || image.isEmpty()) { image.set(target); invalidate(); return; }
        if (Math.abs(image.left - target.left) + Math.abs(image.top - target.top)
            + Math.abs(image.width() - target.width()) + Math.abs(image.height() - target.height()) < .5f) return;
        RectF from = new RectF(image);
        zoom = android.animation.ValueAnimator.ofFloat(0, 1); zoom.setDuration(200);
        zoom.setInterpolator(new android.view.animation.DecelerateInterpolator());
        zoom.addUpdateListener(animation -> {
            float f = (Float) animation.getAnimatedValue();
            image.set(from.left + (target.left - from.left) * f, from.top + (target.top - from.top) * f,
                from.right + (target.right - from.right) * f, from.bottom + (target.bottom - from.bottom) * f);
            invalidate();
        });
        zoom.start();
    }

    private boolean outwardAtEdge() {
        float edge = Ui.dp(getContext(), 36), delta = Ui.dp(getContext(), 2);
        boolean left = handle == 1 || handle == 4, top = handle == 1 || handle == 2;
        return (left ? pointerX < edge && pointerX < startX - delta && state.crop.left > 0
                     : pointerX > getWidth() - edge && pointerX > startX + delta && state.crop.right < 1)
            || (top ? pointerY < edge && pointerY < startY - delta && state.crop.top > 0
                    : pointerY > getHeight() - edge && pointerY > startY + delta && state.crop.bottom < 1);
    }

    private boolean zoomOutStep() {
        float margin = Ui.dp(getContext(), 24);
        int width = state.imageWidth(bitmap.getWidth(), bitmap.getHeight());
        int height = state.imageHeight(bitmap.getWidth(), bitmap.getHeight());
        float minimum = Math.min((getWidth() - 2 * margin) / width, (getHeight() - 2 * margin) / height);
        float factor = Math.max(.97f, minimum * width / image.width());
        if (factor >= .9999f) return false;
        boolean left = handle == 1 || handle == 4, top = handle == 1 || handle == 2;
        float anchorX = left ? initial.right : initial.left, anchorY = top ? initial.bottom : initial.top;
        float screenX = image.left + anchorX * image.width(), screenY = image.top + anchorY * image.height();
        float w = image.width() * factor, h = image.height() * factor;
        image.set(screenX - anchorX * w, screenY - anchorY * h,
            screenX + (1 - anchorX) * w, screenY + (1 - anchorY) * h);
        return true;
    }

    private void stopMotion() {
        touching = false; removeCallbacks(expandAtEdge);
        if (zoom != null) { zoom.cancel(); zoom = null; }
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { stopMotion(); focusCrop(false); }
    @Override protected void onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow(); }

    @Override public boolean performClick() { super.performClick(); return true; }
}

package com.phantom.p2cx;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
    static int BACKGROUND, SURFACE, SECONDARY, TEXT, MUTED, BORDER, ACCENT;
    static boolean dark;
    static void configure(Context context) {
        dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        BACKGROUND = Color.parseColor(dark ? "#101012" : "#F7F5F2");
        SURFACE = Color.parseColor(dark ? "#1B1B1F" : "#FFFFFF");
        SECONDARY = Color.parseColor(dark ? "#252529" : "#F0EDE9");
        TEXT = Color.parseColor(dark ? "#F6F4F1" : "#171717");
        MUTED = Color.parseColor(dark ? "#A5A2A0" : "#77736F");
        BORDER = Color.parseColor(dark ? "#39383D" : "#E4E0DB");
        ACCENT = Color.parseColor(dark ? "#B9A2D4" : "#9275B8");
    }
    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static LinearLayout root(Activity activity) { return root(activity, false); }
    static LinearLayout root(Activity activity, boolean darkHeader) {
        configure(activity);
        activity.getWindow().setStatusBarColor(darkHeader ? 0xff171717 : BACKGROUND);
        activity.getWindow().setNavigationBarColor(BACKGROUND);
        activity.getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController control = activity.getWindow().getInsetsController();
            if (control != null) control.setSystemBarsAppearance((!dark && !darkHeader ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0)
                | (!dark ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0),
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else activity.getWindow().getDecorView().setSystemUiVisibility((!dark && !darkHeader ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0) | (!dark && android.os.Build.VERSION.SDK_INT >= 27 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(darkHeader ? 0xff171717 : BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top; bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            view.setPadding(darkHeader ? 0 : dp(activity, 18), top, darkHeader ? 0 : dp(activity, 18), darkHeader ? 0 : bottom);
            return insets;
        });
        activity.setContentView(root); root.requestApplyInsets(); return root;
    }
    static TextView text(Context context, String label, float size) {
        TextView view = new TextView(context);
        view.setText(label); view.setTextSize(size); view.setTextColor(TEXT);
        view.setFontFeatureSettings("kern"); view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    static GradientDrawable background(Context context, int color, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(context, 15));
        shape.setStroke(dp(context, 1), selected ? ACCENT : BORDER); return shape;
    }
    static void select(Button button, boolean selected) {
        button.setTextColor(selected ? BACKGROUND : TEXT);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22777777),
            background(button.getContext(), selected ? TEXT : SECONDARY, false), null));
        button.setSelected(selected);
    }
    static Button button(Context context, String label) { return button(context, label, false); }
    static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context); button.setText(label); button.setAllCaps(false);
        button.setTextSize(14); button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(context, 48)); button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(context, 14), dp(context, 5), dp(context, 14), dp(context, 5));
        button.setStateListAnimator(null); select(button, primary); return button;
    }
    static Button icon(Context context, String symbol, String description, boolean onDark) {
        Button button = button(context, symbol); button.setTextSize(24);
        button.setTextColor(onDark ? Color.WHITE : TEXT);
        button.setContentDescription(description);
        GradientDrawable shape = background(context, onDark ? 0xff252525 : BACKGROUND, false); shape.setStroke(0, Color.TRANSPARENT);
        if ("\u2039".equals(symbol) || "\u21bb".equals(symbol)) {
            button.setText("");
            LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{shape, new Symbol("\u2039".equals(symbol), onDark ? Color.WHITE : TEXT)});
            layers.setLayerGravity(1, Gravity.CENTER); layers.setLayerSize(1, dp(context,22), dp(context,22));
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22777777), layers, null));
        } else button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22777777), shape, null));
        return button;
    }
    private static final class Symbol extends android.graphics.drawable.Drawable {
        final boolean back; final android.graphics.Paint paint = new android.graphics.Paint(3);
        Symbol(boolean back, int color) { this.back=back; paint.setColor(color); paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(1.7f); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND); paint.setStrokeJoin(android.graphics.Paint.Join.ROUND); }
        @Override public void draw(android.graphics.Canvas canvas) {
            canvas.save(); canvas.translate(getBounds().left, getBounds().top); canvas.scale(getBounds().width()/24f, getBounds().height()/24f);
            if (back) { android.graphics.Path path = new android.graphics.Path(); path.moveTo(15,4); path.lineTo(7,12); path.lineTo(15,20); canvas.drawPath(path,paint); }
            else { canvas.drawArc(4,4,20,20,-60,300,false,paint); canvas.drawLine(17,2,17,7,paint); canvas.drawLine(17,7,12,7,paint); }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL); return row;
    }
    static void space(LinearLayout row, int width) { View gap = new View(row.getContext()); row.addView(gap, new LinearLayout.LayoutParams(dp(row.getContext(), width), 1)); }
}

package com.phantom.p2cx;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;

/** Compact editor controls with bundled Lucide vector icons. */
final class EditorUi {
    static Button tile(Context context, String label, int icon) {
        Button button = Ui.button(context, label);
        button.setTextSize(12); button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false); button.setMaxLines(2);
        button.setPadding(Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8));
        button.setMinHeight(Ui.dp(context, 68)); button.setMinimumHeight(Ui.dp(context, 68));
        button.setCompoundDrawablePadding(Ui.dp(context, 7));
        icon(button, icon); select(button, false); return button;
    }
    static Button header(Context context, String description, int icon) {
        Button button = tile(context, "", icon); button.setTextSize(0);
        button.setContentDescription(description); button.setCompoundDrawablePadding(0);
        button.setMinHeight(Ui.dp(context, 44)); button.setMinimumHeight(Ui.dp(context, 44));
        return button;
    }
    static void icon(Button button, int resource) {
        Drawable icon = button.getContext().getDrawable(resource).mutate();
        int size = Ui.dp(button.getContext(), 22); icon.setBounds(0, 0, size, size);
        button.setCompoundDrawables(null, icon, null, null);
    }
    static void select(Button button, boolean selected) {
        int foreground = selected ? (Ui.dark ? Ui.BACKGROUND : Color.WHITE) : Ui.TEXT;
        button.setTextColor(foreground); button.setCompoundDrawableTintList(ColorStateList.valueOf(foreground));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22777777),
            Ui.background(button.getContext(), selected ? Ui.ACCENT : Ui.SURFACE, selected), null));
        button.setSelected(selected);
    }
}

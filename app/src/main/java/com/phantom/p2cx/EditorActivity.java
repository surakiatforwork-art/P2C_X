package com.phantom.p2cx;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EditorActivity extends Activity {
    private EditorState state;
    private Uri source;
    private int sourceWidth;
    private int sourceHeight;
    private Bitmap preview;
    private CropView cropView;
    private TextView dimensions;
    private EditText width;
    private EditText height;
    private Button lock;
    private Button rotate;
    private Button rotateLeft;
    private Button flipX;
    private Button flipY;
    private Button use;
    private Button reset;
    private boolean updating;
    private String editorPreferences;
    private int recommendedWidth, recommendedHeight;
    private Button recommended;
    private boolean exporting;
    private final Map<String, Button> ratios = new LinkedHashMap<>();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setResult(RESULT_CANCELED);
        source = getIntent().getData();
        if (source == null) { finish(); return; }
        String target = TargetProfile.target(this);
        editorPreferences = "standalone".equals(target) ? "editor" : "editor." + target;
        recommendedWidth = getIntent().getIntExtra(TargetProfile.WIDTH, 0);
        recommendedHeight = getIntent().getIntExtra(TargetProfile.HEIGHT, 0);
        if (recommendedWidth < 1 || recommendedHeight < 1 || recommendedWidth > 32768 || recommendedHeight > 32768) recommendedWidth = recommendedHeight = 0;
        state = saved != null && saved.containsKey("editor") ? EditorState.restore(saved.getBundle("editor"))
            : EditorState.loadForTarget(getSharedPreferences(editorPreferences, MODE_PRIVATE), getSharedPreferences("editor", MODE_PRIVATE));
        build();
        new Thread(() -> {
            try {
                int[] size = EditorRenderer.dimensions(this, source);
                Bitmap bitmap = Images.decode(this, source, 1600);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) { bitmap.recycle(); return; }
                    sourceWidth = size[0];
                    sourceHeight = size[1];
                    preview = bitmap;
                    state.fit(sourceWidth, sourceHeight);
                    cropView.setImage(preview, sourceWidth, sourceHeight, state, this::cropChanged);
                    refresh();
                    use.setEnabled(true);
                });
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
            Toast.makeText(this, "Cannot open image", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }, "P2CX-preview").start();
    }

    private void build() {
        LinearLayout root = Ui.root(this);
        LinearLayout header = Ui.row(this);
        Button back = EditorUi.header(this, "\u0e01\u0e25\u0e31\u0e1a", R.drawable.ic_editor_chevron_left);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView title = Ui.text(this, "\u0e1b\u0e23\u0e31\u0e1a\u0e20\u0e32\u0e1e", 21); title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        reset = EditorUi.header(this, "\u0e23\u0e35\u0e40\u0e0b\u0e47\u0e15", R.drawable.ic_editor_rotate_ccw);
        reset.setOnClickListener(view -> {
            if (preview == null || exporting) return;
            state = new EditorState(); state.fit(sourceWidth, sourceHeight);
            cropView.setImage(preview, sourceWidth, sourceHeight, state, this::cropChanged);
            refresh();
        });
        header.addView(reset, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        header.setMinimumHeight(Ui.dp(this, 62)); root.addView(header);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        cropView = new CropView(this);
        int screenHeight = Math.round(getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density);
        int previewHeight = Math.max(180, Math.min(600, screenHeight - 410));
        body.addView(cropView, new LinearLayout.LayoutParams(-1, Ui.dp(this, previewHeight)));
        dimensions = Ui.text(this, "\u0e01\u0e33\u0e25\u0e31\u0e07\u0e40\u0e1b\u0e34\u0e14\u0e20\u0e32\u0e1e\u2026", 12); dimensions.setGravity(Gravity.CENTER); dimensions.setTextColor(Ui.MUTED);
        body.addView(dimensions, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
        HorizontalScrollView ratiosScroll = new HorizontalScrollView(this); ratiosScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout ratioRow = Ui.row(this);
        String[][] choices = {{"original", "\u0e2a\u0e31\u0e14\u0e2a\u0e48\u0e27\u0e19\u0e40\u0e14\u0e34\u0e21"}, {"free", "\u0e2d\u0e34\u0e2a\u0e23\u0e30"}, {"1:1", "1:1"}, {"4:3", "4:3"}, {"16:9", "16:9"}};
        for (String[] choice : choices) {
            int icon = "original".equals(choice[0]) ? R.drawable.ic_editor_ratio : "free".equals(choice[0]) ? R.drawable.ic_editor_crop
                : "1:1".equals(choice[0]) ? R.drawable.ic_editor_square : R.drawable.ic_editor_rectangle_horizontal;
            Button button = EditorUi.tile(this, choice[1], icon);
            button.setMinWidth(Ui.dp(this, "original".equals(choice[0]) ? 100 : 70));
            button.setOnClickListener(view -> {
                if (preview == null || exporting) return;
                state.ratio = choice[0]; state.crop.set(0, 0, 1, 1); state.fit(sourceWidth, sourceHeight); refresh();
            });
            ratios.put(choice[0], button);
            ratioRow.addView(button, new LinearLayout.LayoutParams(-2, -2)); Ui.space(ratioRow, 6);
            if ("free".equals(choice[0]) && recommendedWidth > 0) {
                recommended = EditorUi.tile(this, "\u0e41\u0e19\u0e30\u0e19\u0e33", R.drawable.ic_editor_sparkles);
                recommended.setMinWidth(Ui.dp(this, 70));
                recommended.setContentDescription(recommended.getText() + " " + recommendedRatio());
                recommended.setOnClickListener(view -> {
                    if (preview == null || exporting) return;
                    android.widget.PopupMenu menu = new android.widget.PopupMenu(this, recommended);
                    String portrait = recommendationRatio(true), landscape = recommendationRatio(false);
                    menu.getMenu().add(0, 1, 0, "\u0e41\u0e19\u0e27\u0e15\u0e31\u0e49\u0e07 · " + portrait);
                    menu.getMenu().add(0, 2, 1, "\u0e41\u0e19\u0e27\u0e19\u0e2d\u0e19 · " + landscape);
                    menu.setOnMenuItemClickListener(item -> { applyRecommended(item.getItemId() == 1); return true; });
                    menu.show();
                });
                ratioRow.addView(recommended, new LinearLayout.LayoutParams(-2, -2));
                Ui.space(ratioRow, 6);
            }
        }
        ratiosScroll.addView(ratioRow);
        LinearLayout.LayoutParams ratioLayout = new LinearLayout.LayoutParams(-1, -2); ratioLayout.bottomMargin = Ui.dp(this, 12);
        body.addView(ratiosScroll, ratioLayout);
        LinearLayout tools = Ui.row(this);
        rotateLeft = EditorUi.tile(this, "\u0e2b\u0e21\u0e38\u0e19\u0e0b\u0e49\u0e32\u0e22 90\u00b0", R.drawable.ic_editor_rotate_ccw);
        rotateLeft.setOnClickListener(view -> {
            if (preview == null || exporting) return;
            for (int i = 0; i < 3; i++) state.rotateClockwise(sourceWidth, sourceHeight);
            cropView.focusCrop(false); refresh();
        });
        rotate = EditorUi.tile(this, "\u0e2b\u0e21\u0e38\u0e19\u0e02\u0e27\u0e32 90\u00b0", R.drawable.ic_editor_rotate_cw);
        rotate.setOnClickListener(view -> {
            if (preview == null || exporting) return;
            state.rotateClockwise(sourceWidth, sourceHeight); cropView.focusCrop(false); refresh();
        });
        flipX = EditorUi.tile(this, "\u0e0b\u0e49\u0e32\u0e22\u2013\u0e02\u0e27\u0e32", R.drawable.ic_editor_flip_horizontal_2);
        flipX.setContentDescription("\u0e01\u0e25\u0e31\u0e1a\u0e20\u0e32\u0e1e\u0e41\u0e19\u0e27\u0e19\u0e2d\u0e19");
        flipX.setOnClickListener(view -> { if (!exporting) { state.flipX = !state.flipX; refresh(); } });
        flipY = EditorUi.tile(this, "\u0e1a\u0e19\u2013\u0e25\u0e48\u0e32\u0e07", R.drawable.ic_editor_flip_vertical_2);
        flipY.setContentDescription("\u0e01\u0e25\u0e31\u0e1a\u0e20\u0e32\u0e1e\u0e41\u0e19\u0e27\u0e15\u0e31\u0e49\u0e07");
        flipY.setOnClickListener(view -> { if (!exporting) { state.flipY = !state.flipY; refresh(); } });
        Button[] toolButtons = {rotateLeft, rotate, flipX, flipY};
        for (int i = 0; i < toolButtons.length; i++) {
            toolButtons[i].setTextSize(11);
            tools.addView(toolButtons[i], new LinearLayout.LayoutParams(0, -2, 1));
            if (i != toolButtons.length - 1) Ui.space(tools, 7);
        }
        body.addView(tools);
        LinearLayout sizes = Ui.row(this); sizes.setGravity(Gravity.BOTTOM); sizes.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        LinearLayout widthGroup = new LinearLayout(this); widthGroup.setOrientation(LinearLayout.VERTICAL);
        TextView widthLabel = Ui.text(this, "\u0e01\u0e27\u0e49\u0e32\u0e07 \u00b7 px", 12); widthLabel.setTextColor(Ui.MUTED); widthGroup.addView(widthLabel);
        width = number("\u0e04\u0e27\u0e32\u0e21\u0e01\u0e27\u0e49\u0e32\u0e07\u0e40\u0e1b\u0e47\u0e19\u0e1e\u0e34\u0e01\u0e40\u0e0b\u0e25"); widthGroup.addView(width, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        sizes.addView(widthGroup, new LinearLayout.LayoutParams(0, -2, 1)); Ui.space(sizes, 10);
        lock = EditorUi.header(this, "\u0e25\u0e47\u0e2d\u0e01\u0e2a\u0e31\u0e14\u0e2a\u0e48\u0e27\u0e19", R.drawable.ic_editor_lock_keyhole);
        lock.setOnClickListener(view -> { if (exporting || preview == null) return; state.locked = !state.locked; state.refreshSize(sourceWidth, sourceHeight); refresh(); });
        sizes.addView(lock, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48))); Ui.space(sizes, 10);
        LinearLayout heightGroup = new LinearLayout(this); heightGroup.setOrientation(LinearLayout.VERTICAL);
        TextView heightLabel = Ui.text(this, "\u0e2a\u0e39\u0e07 \u00b7 px", 12); heightLabel.setTextColor(Ui.MUTED); heightGroup.addView(heightLabel);
        height = number("\u0e04\u0e27\u0e32\u0e21\u0e2a\u0e39\u0e07\u0e40\u0e1b\u0e47\u0e19\u0e1e\u0e34\u0e01\u0e40\u0e0b\u0e25"); heightGroup.addView(height, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        sizes.addView(heightGroup, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(sizes);
        width.addTextChangedListener(watcher(true)); height.addTextChangedListener(watcher(false));
        scroll.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> fitPreview(scroll, body));
        scroll.post(() -> fitPreview(scroll, body));
        LinearLayout actions = Ui.row(this); actions.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 12));
        Button cancel = Ui.button(this, "\u0e22\u0e01\u0e40\u0e25\u0e34\u0e01"); cancel.setOnClickListener(view -> finish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1)); Ui.space(actions, 10);
        use = Ui.button(this, "\u0e43\u0e0a\u0e49\u0e20\u0e32\u0e1e\u0e19\u0e35\u0e49", true); use.setEnabled(false); use.setOnClickListener(view -> export());
        actions.addView(use, new LinearLayout.LayoutParams(0, -2, 1.7f)); root.addView(actions);
    }

    private void fitPreview(android.widget.ScrollView scroll, LinearLayout body) {
        if (scroll.getHeight() <= 0) return;
        int controls = body.getPaddingTop() + body.getPaddingBottom();
        for (int i = 0; i < body.getChildCount(); i++) {
            android.view.View child = body.getChildAt(i);
            if (child == cropView || child.getVisibility() == android.view.View.GONE) continue;
            android.view.ViewGroup.MarginLayoutParams layout = (android.view.ViewGroup.MarginLayoutParams) child.getLayoutParams();
            controls += child.getMeasuredHeight() + layout.topMargin + layout.bottomMargin;
        }
        int height = Math.max(Ui.dp(this, 160), Math.min(Ui.dp(this, 620), scroll.getHeight() - controls));
        if (Math.abs(cropView.getLayoutParams().height - height) > 1) {
            android.view.ViewGroup.LayoutParams layout = cropView.getLayoutParams(); layout.height = height; cropView.setLayoutParams(layout);
        }
    }

    private void applyRecommended(boolean portrait) {
        if (preview == null || exporting || recommendedWidth <= 0) return;
        state.ratio = recommendationRatio(portrait); state.crop.set(0, 0, 1, 1);
        state.customSize = false; state.locked = true;
        state.fit(sourceWidth, sourceHeight); refresh();
    }

    private String recommendationRatio(boolean portrait) {
        int shortSide = Math.min(recommendedWidth, recommendedHeight), longSide = Math.max(recommendedWidth, recommendedHeight);
        int a = shortSide, b = longSide;
        while (b != 0) { int next = a % b; a = b; b = next; }
        return portrait ? (shortSide / a) + ":" + (longSide / a) : (longSide / a) + ":" + (shortSide / a);
    }

    private String recommendedRatio() {
        int a = recommendedWidth, b = recommendedHeight;
        while (b != 0) { int next = a % b; a = b; b = next; }
        return (recommendedWidth / a) + ":" + (recommendedHeight / a);
    }

    private EditText number(String description) {
        EditText field = new EditText(this);
        field.setTextColor(Ui.TEXT);
        field.setBackground(Ui.background(this, Ui.SURFACE, false));
        field.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        field.setTextSize(15);
        field.setSingleLine();
        field.setGravity(Gravity.CENTER);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
        field.setContentDescription(description);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        field.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
            return true;
        });
        return field;
    }

    private TextWatcher watcher(boolean horizontal) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (updating || exporting || preview == null) return;
                int value;
                try { value = Integer.parseInt(text.toString()); } catch (NumberFormatException invalid) { return; }
                if (value < 1) return;
                state.customSize = true;
                float ratio = state.imageWidth(sourceWidth, sourceHeight) * state.crop.width() / (state.imageHeight(sourceWidth, sourceHeight) * state.crop.height());
                updating = true;
                if (horizontal) {
                    state.width = value;
                    if (state.locked) { state.height = Math.max(1, Math.round(value / ratio)); height.setText(String.valueOf(state.height)); }
                } else {
                    state.height = value;
                    if (state.locked) { state.width = Math.max(1, Math.round(value * ratio)); width.setText(String.valueOf(state.width)); }
                }
                updating = false;
        dimensions.setText(state.width + " \u00d7 " + state.height + " px");
            }
            @Override public void afterTextChanged(Editable editable) { }
        };
    }

    private void cropChanged() {
        state.refreshSize(sourceWidth, sourceHeight);
        refresh();
    }

    private void refresh() {
        updating = true;
        width.setText(String.valueOf(state.width));
        height.setText(String.valueOf(state.height));
        updating = false;
        dimensions.setText(state.width + " \u00d7 " + state.height + " px");
        EditorUi.icon(lock, state.locked ? R.drawable.ic_editor_lock_keyhole : R.drawable.ic_editor_lock_keyhole_open);
        EditorUi.select(lock, state.locked);
        lock.setContentDescription(state.locked ? "\u0e25\u0e47\u0e2d\u0e01\u0e2a\u0e31\u0e14\u0e2a\u0e48\u0e27\u0e19\u0e02\u0e19\u0e32\u0e14 \u0e41\u0e15\u0e30\u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e1b\u0e25\u0e14\u0e25\u0e47\u0e2d\u0e01" : "\u0e1b\u0e25\u0e14\u0e25\u0e47\u0e2d\u0e01\u0e2a\u0e31\u0e14\u0e2a\u0e48\u0e27\u0e19\u0e02\u0e19\u0e32\u0e14 \u0e41\u0e15\u0e30\u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e25\u0e47\u0e2d\u0e01");
        rotate.setContentDescription("Rotate clockwise 90 degrees; current " + state.rotation);
        EditorUi.select(flipX, state.flipX);
        EditorUi.select(flipY, state.flipY);
        for (Map.Entry<String, Button> ratio : ratios.entrySet()) {
            EditorUi.select(ratio.getValue(), ratio.getKey().equals(state.ratio));
        }
        if (recommended != null) {
            float aspect = state.aspect(sourceWidth, sourceHeight);
            EditorUi.select(recommended, !"original".equals(state.ratio) && !"free".equals(state.ratio)
                && (Math.abs(aspect - recommendedWidth / (float) recommendedHeight) < .0001f
                || Math.abs(aspect - recommendedHeight / (float) recommendedWidth) < .0001f));
        }
        cropView.focusCrop(true);
        cropView.invalidate();
    }

    private void export() {
        if (preview == null || exporting) return;
        if (!RemoteAccess.allowed(this)) { Toast.makeText(this, RemoteAccess.status(this), Toast.LENGTH_LONG).show(); setResult(RESULT_CANCELED); finish(); return; }
        if (width.getText().length() == 0 || height.getText().length() == 0) {
            Toast.makeText(this, "Enter image width and height", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            state.width = Integer.parseInt(width.getText().toString());
            state.height = Integer.parseInt(height.getText().toString());
            if (state.width < 1 || state.height < 1) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            Toast.makeText(this, "Enter positive image dimensions", Toast.LENGTH_SHORT).show();
            return;
        }
        EditorState snapshot = EditorState.restore(state.bundle());
        exporting = true;
        use.setEnabled(false);
                    use.setText("\u0e43\u0e0a\u0e49\u0e20\u0e32\u0e1e\u0e19\u0e35\u0e49");
        cropView.setEnabled(false);
        width.setEnabled(false);
        height.setEnabled(false);
        new Thread(() -> {
            try {
                File file = EditorRenderer.render(this, source, snapshot, sourceWidth, sourceHeight);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) { file.delete(); return; }
                    if (!RemoteAccess.allowed(this)) { file.delete(); setResult(RESULT_CANCELED); finish(); return; }
                    UsageCounters.record(this, "confirmed");
                    snapshot.save(getSharedPreferences(editorPreferences, MODE_PRIVATE));
                    Uri uri = Uri.parse("content://com.phantom.p2cx.images/" + file.getName());
                    Intent result = new Intent().setDataAndType(uri, "image/jpeg");
                    result.setClipData(ClipData.newRawUri("P2C_X image", uri));
                    result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    result.putExtra("p2cx.edited", true);
                    setResult(RESULT_OK, result);
                    finish();
                });
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    exporting = false;
                    use.setEnabled(true);
                    use.setText("\u0e43\u0e0a\u0e49\u0e20\u0e32\u0e1e\u0e19\u0e35\u0e49");
                    cropView.setEnabled(true);
                    width.setEnabled(true);
                    height.setEnabled(true);
                    Toast.makeText(this, error instanceof OutOfMemoryError ? "\u0e2b\u0e19\u0e48\u0e27\u0e22\u0e04\u0e27\u0e32\u0e21\u0e08\u0e33\u0e44\u0e21\u0e48\u0e1e\u0e2d \u0e01\u0e23\u0e38\u0e13\u0e32\u0e25\u0e14\u0e02\u0e19\u0e32\u0e14\u0e20\u0e32\u0e1e" : error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "P2CX-export").start();
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        if (state != null) saved.putBundle("editor", state.bundle());
        super.onSaveInstanceState(saved);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (preview != null) { preview.recycle(); preview = null; }
    }
}

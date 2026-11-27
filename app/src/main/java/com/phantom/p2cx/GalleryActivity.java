package com.phantom.p2cx;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Image-only MediaStore gallery. A tap opens the editor directly. */
public final class GalleryActivity extends Activity {
    private final ArrayList<Photo> photos = new ArrayList<>(), visible = new ArrayList<>();
    private final LinkedHashMap<String, String> albums = new LinkedHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final android.util.LruCache<String, Bitmap> thumbnails = new android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private TextView status;
    private Button permission, all, album;
    private GridAdapter adapter;
    private GridView grid;
    private Uri selected;
    private String albumId = "";
    private int generation, filterGeneration;
    private android.content.SharedPreferences history;
    private boolean restoring, loaded;
    private boolean editing;
    private static final class Photo {
        final Uri uri; final String album, name;
        Photo(Uri uri, String album, String name) { this.uri = uri; this.album = album; this.name = name; }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); setResult(RESULT_CANCELED);
        if (!RemoteAccess.allowed(this)) { startActivity(new Intent(this, MainActivity.class)); finish(); return; }
        if (saved == null) UsageCounters.record(this, "opens");
        history = TargetProfile.gallery(this);
        albumId = history.getString("album", "");
        if (saved != null) { selected = saved.getParcelable("selected"); albumId = saved.getString("album", ""); editing = saved.getBoolean("editing"); }
        LinearLayout root = Ui.root(this, true);
        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        Button back = Ui.icon(this, "\u2039", "\u0e01\u0e25\u0e31\u0e1a", true); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setGravity(Gravity.CENTER);
        TextView title = Ui.text(this, "P2C_X", 24); title.setTextColor(Color.WHITE); title.setGravity(Gravity.CENTER); titles.addView(title);
        TextView subtitle = Ui.text(this, "\u0e41\u0e01\u0e25\u0e40\u0e25\u0e2d\u0e23\u0e35\u0e48", 12); subtitle.setTextColor(0xffc4c0bc); subtitle.setGravity(Gravity.CENTER); titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        View balance = new View(this); header.addView(balance, new LinearLayout.LayoutParams(Ui.dp(this, 44), 1));
        header.setMinimumHeight(Ui.dp(this, 76)); root.addView(header);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(Ui.background(this, Ui.BACKGROUND, false));
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 10));
        body.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = android.os.Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.systemBars()).bottom : insets.getSystemWindowInsetBottom();
            view.setPadding(Ui.dp(this,16), Ui.dp(this,16), Ui.dp(this,16), Ui.dp(this,10)+bottom);
            return insets;
        });
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        HorizontalScrollView filters = new HorizontalScrollView(this); filters.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(this);
        all = Ui.button(this, "\u0e23\u0e39\u0e1b\u0e20\u0e32\u0e1e\u0e17\u0e31\u0e49\u0e07\u0e2b\u0e21\u0e14"); all.setOnClickListener(v -> { selectAlbum(""); });
        all.setMinHeight(Ui.dp(this,44)); all.setMinimumHeight(Ui.dp(this,44)); all.setTextSize(13);
        chips.addView(all, new LinearLayout.LayoutParams(-2, -2)); Ui.space(chips, 8);
        album = Ui.button(this, "\u0e2d\u0e31\u0e25\u0e1a\u0e31\u0e49\u0e21 \u2304"); album.setOnClickListener(v -> chooseAlbum());
        album.setMinHeight(Ui.dp(this,44)); album.setMinimumHeight(Ui.dp(this,44)); album.setTextSize(13);
        chips.addView(album, new LinearLayout.LayoutParams(-2, -2));
        filters.addView(chips); body.addView(filters);
        permission = Ui.button(this, "\u0e40\u0e25\u0e37\u0e2d\u0e01\u0e20\u0e32\u0e1e\u0e17\u0e35\u0e48\u0e2d\u0e19\u0e38\u0e0d\u0e32\u0e15\u0e43\u0e2b\u0e49\u0e40\u0e02\u0e49\u0e32\u0e16\u0e36\u0e07");
        permission.setOnClickListener(v -> requestImages()); body.addView(permission);
        status = Ui.text(this, "\u0e01\u0e33\u0e25\u0e31\u0e07\u0e42\u0e2b\u0e25\u0e14\u0e20\u0e32\u0e1e\u2026", 12); status.setTextColor(Ui.MUTED);
        status.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12)); body.addView(status);
        grid = new GridView(this); grid.setNumColumns(3); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.dp(this, 5)); grid.setVerticalSpacing(Ui.dp(this, 5));
        grid.setSelector(android.R.color.transparent);
        adapter = new GridAdapter(); grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (editing || position >= visible.size()) return;
            selected = visible.get(position).uri; adapter.notifyDataSetChanged();
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            rememberPosition();
            editing = true;
            try { startActivityForResult(TargetProfile.forward(this, new Intent(this, EditorActivity.class).setData(selected)), 20); }
            catch (RuntimeException error) { editing = false; Toast.makeText(this, "\u0e40\u0e1b\u0e34\u0e14\u0e20\u0e32\u0e1e\u0e44\u0e21\u0e48\u0e44\u0e14\u0e49 \u0e01\u0e23\u0e38\u0e13\u0e32\u0e25\u0e2d\u0e07\u0e2d\u0e35\u0e01\u0e04\u0e23\u0e31\u0e49\u0e07", Toast.LENGTH_SHORT).show(); }
        });
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int state) { if (state == SCROLL_STATE_IDLE) rememberPosition(); }
            public void onScroll(AbsListView view, int first, int count, int total) { }
        });
        body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = Ui.text(this, "\u0e41\u0e15\u0e30\u0e20\u0e32\u0e1e\u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e1b\u0e23\u0e31\u0e1a\u0e41\u0e25\u0e30\u0e43\u0e0a\u0e49\u0e07\u0e32\u0e19", 12); hint.setTextColor(Ui.MUTED);
        hint.setPadding(0, Ui.dp(this, 12), 0, 0); body.addView(hint);
        updatePermission(); if (canRead()) load(); else requestImages();
    }
    private boolean fullAccess() {
        return checkSelfPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean canRead() { return fullAccess() || (Build.VERSION.SDK_INT >= 34 && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED); }
    private void updatePermission() { permission.setVisibility(fullAccess() ? View.GONE : View.VISIBLE); }
    private void requestImages() {
        if (Build.VERSION.SDK_INT >= 34) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, 10);
        else requestPermissions(new String[]{Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE}, 10);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 10) { updatePermission(); if (canRead()) load(); else status.setText("\u0e2d\u0e19\u0e38\u0e0d\u0e32\u0e15\u0e40\u0e02\u0e49\u0e32\u0e16\u0e36\u0e07\u0e20\u0e32\u0e1e\u0e40\u0e1e\u0e37\u0e48\u0e2d\u0e40\u0e1b\u0e34\u0e14\u0e41\u0e01\u0e25\u0e40\u0e25\u0e2d\u0e23\u0e35\u0e48"); }
    }
    private void chooseAlbum() {
        ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
        ids.add(""); names.add("\u0e23\u0e39\u0e1b\u0e20\u0e32\u0e1e\u0e17\u0e31\u0e49\u0e07\u0e2b\u0e21\u0e14");
        for (Map.Entry<String, String> entry : albums.entrySet()) { ids.add(entry.getKey()); names.add(entry.getValue()); }
        new AlertDialog.Builder(this).setTitle("\u0e2d\u0e31\u0e25\u0e1a\u0e31\u0e49\u0e21").setSingleChoiceItems(names.toArray(new String[0]), ids.indexOf(albumId),
            (dialog, which) -> { selectAlbum(ids.get(which)); dialog.dismiss(); }).setNegativeButton("\u0e22\u0e01\u0e40\u0e25\u0e34\u0e01", null).show();
    }
    private void load() {
        int token = ++generation;
        workers.execute(() -> {
            ArrayList<Photo> loaded = new ArrayList<>();
            try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{"_id", "bucket_id", "bucket_display_name"}, null, null, "date_added DESC, _id DESC")) {
                if (cursor != null) while (cursor.moveToNext()) loaded.add(new Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)), cursor.getString(1), cursor.getString(2)));
                runOnUiThread(() -> {
                    if (isDestroyed() || token != generation) return;
                    photos.clear(); photos.addAll(loaded); albums.clear();
                    for (Photo photo : photos) if (photo.album != null) albums.put(photo.album, photo.name == null ? "\u0e44\u0e21\u0e48\u0e21\u0e35\u0e0a\u0e37\u0e48\u0e2d" : photo.name);
                    if (!albumId.isEmpty() && !albums.containsKey(albumId)) {
                        albumId = ""; history.edit().putString("album", "").apply();
                    }
                    GalleryActivity.this.loaded = true;
                    filter();
                });
            } catch (RuntimeException error) { runOnUiThread(() -> { if (!isDestroyed()) status.setText("\u0e2d\u0e48\u0e32\u0e19\u0e20\u0e32\u0e1e\u0e44\u0e21\u0e48\u0e44\u0e14\u0e49 \u0e01\u0e23\u0e38\u0e13\u0e32\u0e15\u0e23\u0e27\u0e08\u0e2a\u0e2d\u0e1a\u0e2a\u0e34\u0e17\u0e18\u0e34\u0e4c"); }); }
        });
    }
    private void filter() {
        visible.clear();
        for (Photo photo : photos) if (albumId.isEmpty() || albumId.equals(photo.album)) visible.add(photo);
        status.setText(visible.size() + " \u0e20\u0e32\u0e1e" + (fullAccess() ? "" : " \u00b7 \u0e20\u0e32\u0e1e\u0e17\u0e35\u0e48\u0e2d\u0e19\u0e38\u0e0d\u0e32\u0e15"));
        Ui.select(all, albumId.isEmpty()); Ui.select(album, !albumId.isEmpty());
        album.setText(albumId.isEmpty() ? "\u0e2d\u0e31\u0e25\u0e1a\u0e31\u0e49\u0e21 \u2304" : albums.get(albumId) + " \u2304");
        final int restoreToken = ++filterGeneration;
        restoring = true;
        adapter.notifyDataSetChanged();
        final String restoredAlbum = albumId;
        final String anchor = history.getString("anchor." + albumId, "");
        int position = history.getInt("position." + albumId, 0);
        for (int i = 0; i < visible.size(); i++) if (visible.get(i).uri.toString().equals(anchor)) { position = i; break; }
        final int restoredPosition = Math.max(0, Math.min(position, visible.size() - 1));
        final int offset = history.getInt("offset." + albumId, 0);
        grid.post(() -> {
            if (isDestroyed() || restoreToken != filterGeneration || !restoredAlbum.equals(albumId)) return;
            grid.setSelectionFromTop(restoredPosition, offset);
            grid.post(() -> { if (restoreToken == filterGeneration) restoring = false; });
        });
    }
    private void selectAlbum(String id) {
        if (id.equals(albumId)) return;
        rememberPosition();
        albumId = id;
        history.edit().putString("album", id).apply();
        filter();
    }
    private void rememberPosition() {
        if (!loaded || restoring || grid == null || grid.getChildCount() == 0 || visible.isEmpty()) return;
        int first = grid.getFirstVisiblePosition();
        if (first < 0 || first >= visible.size()) return;
        history.edit().putString("album", albumId)
            .putString("anchor." + albumId, visible.get(first).uri.toString())
            .putInt("position." + albumId, first)
            .putInt("offset." + albumId, grid.getChildAt(0).getTop() - grid.getListPaddingTop()).apply();
    }
    @Override protected void onPause() { rememberPosition(); super.onPause(); }
    private final class GridAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View recycled, ViewGroup parent) {
            Thumbnail image = recycled instanceof Thumbnail ? (Thumbnail) recycled : new Thumbnail();
            int size = grid.getColumnWidth() > 0 ? grid.getColumnWidth() : (getResources().getDisplayMetrics().widthPixels - Ui.dp(GalleryActivity.this, 42)) / 3;
            image.setLayoutParams(new AbsListView.LayoutParams(-1, size));
            Photo photo = visible.get(position); String key = photo.uri.toString(); image.key = key;
            image.chosen = photo.uri.equals(selected); image.setSelected(image.chosen); image.bitmap = thumbnails.get(key); image.invalidate();
            image.setContentDescription("\u0e20\u0e32\u0e1e " + (position + 1) + (image.chosen ? " \u0e40\u0e25\u0e37\u0e2d\u0e01\u0e41\u0e25\u0e49\u0e27" : ""));
            if (image.bitmap == null && !workers.isShutdown()) workers.execute(() -> {
                if (!key.equals(image.key)) return;
                try {
                    Bitmap thumbnail = thumbnails.get(key);
                    if (thumbnail == null) { thumbnail = Build.VERSION.SDK_INT >= 29
                        ? getContentResolver().loadThumbnail(photo.uri, new android.util.Size(300, 300), null)
                        : Images.decode(GalleryActivity.this, photo.uri, 300); thumbnails.put(key, thumbnail); }
                    final Bitmap ready = thumbnail;
                    runOnUiThread(() -> { if (!isDestroyed() && key.equals(image.key)) { image.bitmap = ready; image.invalidate(); } });
                } catch (Exception | OutOfMemoryError ignored) { }
            });
            return image;
        }
    }
    private final class Thumbnail extends View {
        volatile String key; Bitmap bitmap; boolean chosen;
        final Paint paint = new Paint(3); final RectF bounds = new RectF(); final Path clip = new Path();
        Thumbnail() { super(GalleryActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float radius = Ui.dp(getContext(), 11);
            bounds.set(0, 0, getWidth(), getHeight()); clip.reset(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.save(); canvas.clipPath(clip); canvas.drawColor(Ui.SECONDARY);
            if (bitmap != null && !bitmap.isRecycled()) {
                float scale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
                float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
                canvas.drawBitmap(bitmap, null, new RectF((getWidth()-w)/2, (getHeight()-h)/2, (getWidth()+w)/2, (getHeight()+h)/2), paint);
            }
            canvas.restore();
            if (chosen) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(getContext(), 3)); paint.setColor(Ui.TEXT);
                bounds.inset(Ui.dp(getContext(), 1.5f), Ui.dp(getContext(), 1.5f)); canvas.drawRoundRect(bounds, radius, radius, paint);
                paint.setStyle(Paint.Style.FILL); float x = getWidth()-Ui.dp(getContext(), 16), y = Ui.dp(getContext(), 16);
                canvas.drawCircle(x, y, Ui.dp(getContext(), 10), paint);
                paint.setColor(Ui.BACKGROUND); paint.setStrokeWidth(Ui.dp(getContext(), 2)); paint.setStyle(Paint.Style.STROKE);
                Path mark = new Path(); mark.moveTo(x-Ui.dp(getContext(),4), y); mark.lineTo(x-Ui.dp(getContext(),1), y+Ui.dp(getContext(),3)); mark.lineTo(x+Ui.dp(getContext(),5), y-Ui.dp(getContext(),4)); canvas.drawPath(mark, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (request == 20) editing = false;
        if (request == 20 && result == RESULT_OK && data != null && data.getData() != null && "com.phantom.p2cx.images".equals(data.getData().getAuthority())) {
            Intent response = new Intent().setDataAndType(data.getData(), "image/jpeg");
            response.setClipData(ClipData.newRawUri("P2C_X image", data.getData()));
            response.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setResult(RESULT_OK, response); finish();
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putParcelable("selected", selected); state.putString("album", albumId); state.putBoolean("editing", editing); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { generation++; workers.shutdownNow(); super.onDestroy(); }
}

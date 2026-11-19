package com.phantom.p2cx;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.OutputStream;

public final class PickerActivity extends Activity {
    private static final int PICK = 410;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        if (state != null) {
            return;
        }
        Intent picker = new Intent(this, GalleryActivity.class);

        try { startActivityForResult(picker, PICK); }
        catch (Exception error) { finish(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK) return;
        if (result != RESULT_OK || data == null || data.getData() == null) { finish(); return; }
        Uri source = data.getData();
        Uri output = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                boolean edited = output != null && "com.phantom.p2cx.images".equals(source.getAuthority());
                if (!edited) bitmap = Images.decode(this, source, output == null ? 256 : 4096);
                Intent response = new Intent();
                if (output != null) {
                    if (!"content".equals(output.getScheme())) throw new IllegalArgumentException("Unsupported output URI");
                    try (OutputStream destination = getContentResolver().openOutputStream(output, "wt")) {
                        if (destination == null) throw new java.io.IOException("Cannot open output");
                        if (edited) CaptureOutput.copy(this, source, destination);
                        else Images.jpeg(bitmap, destination);
                    }
                } else {
                    response.putExtra("data", bitmap);
                    bitmap = null;
                }
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        setResult(RESULT_OK, response);
                    }
                    finish();
                });
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Cannot send image", Toast.LENGTH_LONG).show();
                    finish();
                });
            } finally {
                if (bitmap != null) bitmap.recycle();
            }
        }, "P2CX-image").start();
    }

}

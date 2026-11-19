package com.phantom.p2cx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class Images {
    private Images() { }

    static Bitmap decode(Context context, Uri uri, int maxEdge) throws IOException {
        Bitmap decoded;
        if (Build.VERSION.SDK_INT >= 28) {
            decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri),
                (decoder, info, source) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    double scale = Math.min(1.0, maxEdge / (double) Math.max(width, height));
                    decoder.setTargetSize(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
                });
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image");
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > maxEdge) options.inSampleSize *= 2;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(input, null, options);
            }
            if (decoded == null) throw new IOException("Cannot decode image");
            int orientation;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            }
            Matrix matrix = new Matrix();
            switch (orientation) {
                case 2: matrix.setScale(-1, 1); break;
                case 3: matrix.setRotate(180); break;
                case 4: matrix.setScale(1, -1); break;
                case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case 6: matrix.setRotate(90); break;
                case 7: matrix.setRotate(270); matrix.postScale(-1, 1); break;
                case 8: matrix.setRotate(270); break;
                default: break;
            }
            if (!matrix.isIdentity()) {
                Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
                if (rotated != decoded) decoded.recycle();
                decoded = rotated;
            }
        }
        if (!decoded.hasAlpha()) return decoded;
        Bitmap opaque = Bitmap.createBitmap(decoded.getWidth(), decoded.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(opaque);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(decoded, 0, 0, null);
        decoded.recycle();
        return opaque;
    }

    static void jpeg(Bitmap bitmap, OutputStream output) throws IOException {
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw new IOException("Cannot encode JPEG");
        output.flush();
    }
}

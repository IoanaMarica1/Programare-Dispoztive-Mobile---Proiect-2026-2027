package com.wanderly.ui.profile;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns any picked photo (JPEG, PNG, HEIC, WebP...) into a small JPEG before upload.
 * The backend only accepts JPEG/PNG up to 5 MB, and resizes to 512px anyway.
 * Must run off the main thread.
 */
final class ImageCompressor {

    private static final int MAX_DIMENSION = 1024;
    private static final int JPEG_QUALITY = 85;

    private ImageCompressor() {
    }

    static byte[] toJpeg(ContentResolver resolver, Uri uri) throws IOException {
        Bitmap bitmap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? decodeScaled(resolver, uri)
                : decodeSampled(resolver, uri);
        if (bitmap == null) throw new IOException("Unsupported image");

        // JPEG has no transparency; without this, transparent PNG areas turn black.
        if (bitmap.hasAlpha()) {
            Bitmap opaque = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle();
            bitmap = opaque;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    /** ImageDecoder also applies the EXIF rotation, so camera photos are not sideways. */
    @RequiresApi(Build.VERSION_CODES.P)
    private static Bitmap decodeScaled(ContentResolver resolver, Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int longest = Math.max(width, height);
            if (longest > MAX_DIMENSION) {
                float scale = (float) MAX_DIMENSION / longest;
                decoder.setTargetSize(Math.round(width * scale), Math.round(height * scale));
            }
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }

    /** Android 8 fallback: decode at a power-of-two sample size close to the limit. */
    private static Bitmap decodeSampled(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, options);
        }
        int sample = 1;
        while (Math.max(options.outWidth, options.outHeight) / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        try (InputStream in = resolver.openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }
}

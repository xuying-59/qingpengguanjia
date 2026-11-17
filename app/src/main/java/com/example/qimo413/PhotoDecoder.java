package com.example.qimo413;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

/** Reads a bounded bitmap for all local photo workflows. */
final class PhotoDecoder {
    private PhotoDecoder() {}

    static Bitmap read(ContentResolver resolver, Uri uri) throws IOException {
        return read(resolver, uri, 1280);
    }

    static Bitmap read(ContentResolver resolver, Uri uri, int maxDimension) throws IOException {
        int targetDimension = Math.max(1, maxDimension);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取照片");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("照片格式无法识别");
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > targetDimension) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream input = resolver.openInputStream(uri)) {
            Bitmap bitmap = input == null ? null : BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("照片解码失败");
            return bitmap;
        }
    }
}

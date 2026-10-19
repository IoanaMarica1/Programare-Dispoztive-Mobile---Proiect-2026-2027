package com.wanderly.ui.common;

import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.wanderly.data.remote.ApiClient;

import java.util.Locale;

/**
 * An avatar is an initial in a colored circle with the uploaded photo drawn on top.
 * Without a photo, or while it loads, the initial shows through.
 */
public final class Avatars {

    private Avatars() {
    }

    public static void bind(ImageView photo, TextView initial, String name, String pictureUrl) {
        initial.setText(initialOf(name));

        String url = ApiClient.absoluteUrl(pictureUrl);
        if (url == null) {
            Glide.with(photo).clear(photo);
            return;
        }
        Glide.with(photo).load(url).circleCrop().into(photo);
    }

    public static String initialOf(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}

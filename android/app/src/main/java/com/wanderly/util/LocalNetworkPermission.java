package com.wanderly.util;

import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.ComponentActivity;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * Android 17 (API 37) drops connections to private addresses (10.0.2.2, 192.168.x.x) unless
 * ACCESS_LOCAL_NETWORK is granted. Ask for it on top of a real screen, never on a blank launcher.
 * Call from onCreate().
 */
public final class LocalNetworkPermission {

    private static final String PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final int API_37 = 37;

    private LocalNetworkPermission() { }

    public static void request(ComponentActivity activity) {
        if (Build.VERSION.SDK_INT < API_37
                || ContextCompat.checkSelfPermission(activity, PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { })
                .launch(PERMISSION);
    }
}

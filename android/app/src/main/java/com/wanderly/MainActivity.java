package com.wanderly;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.wanderly.data.local.TokenManager;
import com.wanderly.ui.auth.LoginActivity;
import com.wanderly.ui.home.HomeActivity;

public class MainActivity extends AppCompatActivity {

    // Android 17 (API 37) silently drops connections to private addresses (10.0.2.2, 192.168.x.x)
    // unless this runtime permission is granted, so requests to the dev backend just time out.
    private static final String LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final int API_37 = 37;

    private final ActivityResultLauncher<String> localNetworkRequest =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> openNext());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= API_37
                && ContextCompat.checkSelfPermission(this, LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            localNetworkRequest.launch(LOCAL_NETWORK);
        } else {
            openNext();
        }
    }

    private void openNext() {
        TokenManager tokenManager = new TokenManager(this);
        Class<?> next = tokenManager.isLoggedIn() ? HomeActivity.class : LoginActivity.class;

        startActivity(new Intent(this, next));
        finish();
    }
}

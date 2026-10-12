package com.wanderly;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.wanderly.data.TokenManager;
import com.wanderly.ui.auth.LoginActivity;
import com.wanderly.ui.home.HomeActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TokenManager tokenManager = new TokenManager(this);
        Class<?> next = tokenManager.isLoggedIn() ? HomeActivity.class : LoginActivity.class;

        startActivity(new Intent(this, next));
        finish();
    }
}
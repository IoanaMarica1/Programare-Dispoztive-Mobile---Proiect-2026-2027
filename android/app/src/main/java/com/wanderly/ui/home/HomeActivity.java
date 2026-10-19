package com.wanderly.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wanderly.R;
import com.wanderly.data.local.TokenManager;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.UserResponse;
import com.wanderly.ui.auth.LoginActivity;
import com.wanderly.ui.common.Avatars;
import com.wanderly.ui.preferences.PreferencesActivity;
import com.wanderly.ui.profile.ProfileActivity;

import java.util.Calendar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private TextView greetingText, welcomeText, avatarInitial, emailText, addressText, ageText;
    private ImageView avatarPhoto;
    private ProgressBar progressBar;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tokenManager = new TokenManager(this);

        greetingText = findViewById(R.id.greetingText);
        welcomeText = findViewById(R.id.welcomeText);
        avatarInitial = findViewById(R.id.avatarInitial);
        avatarPhoto = findViewById(R.id.avatarPhoto);
        emailText = findViewById(R.id.emailText);
        addressText = findViewById(R.id.addressText);
        ageText = findViewById(R.id.ageText);
        progressBar = findViewById(R.id.progressBar);

        greetingText.setText(greetingForNow());

        // The preference wizard is the first step of Start Adventure (story B1).
        findViewById(R.id.startAdventureButton).setOnClickListener(v ->
                startActivity(new Intent(this, PreferencesActivity.class)));
        findViewById(R.id.avatarButton).setOnClickListener(v -> openProfile());
        findViewById(R.id.profileCard).setOnClickListener(v -> openProfile());
        findViewById(R.id.logoutButton).setOnClickListener(v -> logout());
    }

    // Reloads so changes made on the profile screen show up here too.
    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
    }

    private void openProfile() {
        startActivity(new Intent(this, ProfileActivity.class));
    }

    private void loadProfile() {
        progressBar.setVisibility(View.VISIBLE);

        ApiClient.getService(this)
                .getProfile()
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<UserResponse> call,
                                           @NonNull Response<UserResponse> response) {
                        if (isDestroyed()) return;
                        progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            showUser(response.body());
                        } else if (response.code() == 401) {
                            Toast.makeText(HomeActivity.this,
                                    "Your session has expired. Please log in again.",
                                    Toast.LENGTH_LONG).show();
                            logout();
                        } else {
                            Toast.makeText(HomeActivity.this,
                                    "Could not load your profile.", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(HomeActivity.this,
                                "Cannot reach the server.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showUser(UserResponse user) {
        String name = user.name != null ? user.name.trim() : "";

        welcomeText.setText(firstName(name));
        Avatars.bind(avatarPhoto, avatarInitial, name, user.profilePicture);
        emailText.setText(user.email);
        addressText.setText(user.address);
        ageText.setText(user.age != null ? getString(R.string.age_value, user.age) : "—");
    }

    /** "Ioana Marica" → "Ioana" */
    private String firstName(String fullName) {
        if (fullName.isEmpty()) return "";
        return fullName.split("\\s+")[0];
    }

    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.greeting_morning);
        if (hour < 18) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }

    private void logout() {
        tokenManager.clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
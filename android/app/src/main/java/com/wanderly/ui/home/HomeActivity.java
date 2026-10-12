package com.wanderly.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wanderly.R;
import com.wanderly.data.TokenManager;
import com.wanderly.network.ApiClient;
import com.wanderly.network.dto.UserResponse;
import com.wanderly.ui.auth.LoginActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private TextView welcomeText, detailsText;
    private ProgressBar progressBar;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tokenManager = new TokenManager(this);

        welcomeText = findViewById(R.id.welcomeText);
        detailsText = findViewById(R.id.detailsText);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.logoutButton).setOnClickListener(v -> logout());

        loadProfile();
    }

    private void loadProfile() {
        progressBar.setVisibility(View.VISIBLE);

        ApiClient.getService(this)
                .getProfile()
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<UserResponse> call,
                                           @NonNull Response<UserResponse> response) {
                        progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            UserResponse user = response.body();
                            welcomeText.setText("Welcome, " + user.name + "!");
                            detailsText.setText(user.email + "\n" + user.address + " · " + user.age + " years");
                        } else if (response.code() == 401) {
                            // Token expirat sau invalid → userul trebuie să se logheze din nou
                            Toast.makeText(HomeActivity.this,
                                    "Your session has expired. Please log in again.",
                                    Toast.LENGTH_LONG).show();
                            logout();
                        } else {
                            detailsText.setText("Could not load your profile.");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        detailsText.setText("Cannot reach the server. Is the backend running?");
                    }
                });
    }

    private void logout() {
        tokenManager.clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
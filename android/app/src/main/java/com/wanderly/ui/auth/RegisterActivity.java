package com.wanderly.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wanderly.R;
import com.wanderly.data.TokenManager;
import com.wanderly.network.ApiClient;
import com.wanderly.network.dto.AuthResponse;
import com.wanderly.network.dto.ErrorResponse;
import com.wanderly.network.dto.RegisterRequest;
import com.wanderly.ui.home.HomeActivity;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout nameLayout, emailLayout, passwordLayout, addressLayout, ageLayout;
    private TextInputEditText nameInput, emailInput, passwordInput, addressInput, ageInput;
    private Button registerButton;
    private ProgressBar progressBar;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        tokenManager = new TokenManager(this);

        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        addressLayout = findViewById(R.id.addressLayout);
        ageLayout = findViewById(R.id.ageLayout);

        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        addressInput = findViewById(R.id.addressInput);
        ageInput = findViewById(R.id.ageInput);

        registerButton = findViewById(R.id.registerButton);
        progressBar = findViewById(R.id.progressBar);
        TextView goToLogin = findViewById(R.id.goToLogin);

        registerButton.setOnClickListener(v -> attemptRegister());
        goToLogin.setOnClickListener(v -> finish()); // înapoi la Login
    }

    private void attemptRegister() {
        clearErrors();

        String name = textOf(nameInput);
        String email = textOf(emailInput);
        String password = textOf(passwordInput);
        String address = textOf(addressInput);
        Integer age = parseAge(textOf(ageInput));

        boolean valid = true;
        if (name.isEmpty()) { nameLayout.setError("Name is required"); valid = false; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailLayout.setError("Enter a valid email"); valid = false; }
        if (password.length() < 8) { passwordLayout.setError("At least 8 characters"); valid = false; }
        if (address.isEmpty()) { addressLayout.setError("Address is required"); valid = false; }
        if (age == null) { ageLayout.setError("Age is required"); valid = false; }
        if (!valid) return;

        setLoading(true);

        RegisterRequest request = new RegisterRequest(email, password, name, address, age);

        ApiClient.getService(this)
                .register(request)
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call,
                                           @NonNull Response<AuthResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            tokenManager.saveToken(response.body().token);
                            goToHome();
                        } else {
                            showServerErrors(ApiClient.parseError(response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                        setLoading(false);
                        Toast.makeText(RegisterActivity.this,
                                "Cannot reach the server. Is the backend running?",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
    private void showServerErrors(ErrorResponse error) {
        if (error == null) {
            Toast.makeText(this, "Registration failed. Please try again.", Toast.LENGTH_LONG).show();
            return;
        }
        if (error.errors != null && !error.errors.isEmpty()) {
            for (Map.Entry<String, String> entry : error.errors.entrySet()) {
                TextInputLayout layout = layoutFor(entry.getKey());
                if (layout != null) layout.setError(entry.getValue());
            }
        } else if (error.status == 409) {
            emailLayout.setError(error.message); // email deja folosit
        } else {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show();
        }
    }

    private TextInputLayout layoutFor(String field) {
        switch (field) {
            case "name": return nameLayout;
            case "email": return emailLayout;
            case "password": return passwordLayout;
            case "address": return addressLayout;
            case "age": return ageLayout;
            default: return null;
        }
    }

    private void clearErrors() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        addressLayout.setError(null);
        ageLayout.setError(null);
    }

    private Integer parseAge(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!loading);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
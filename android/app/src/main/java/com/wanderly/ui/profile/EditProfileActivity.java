package com.wanderly.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.ErrorResponse;
import com.wanderly.data.remote.dto.UserResponse;
import com.wanderly.data.remote.dto.UserUpdateRequest;
import com.wanderly.ui.common.Avatars;
import com.wanderly.ui.common.Session;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_BIO = "bio";
    public static final String EXTRA_PICTURE = "picture";
    public static final String EXTRA_FOCUS_BIO = "focusBio";

    // Same limits as UserUpdateRequest on the backend.
    private static final int MAX_NAME = 100;
    private static final int MAX_CITY = 50;
    private static final int MAX_BIO = 500;

    private TextInputLayout nameLayout, addressLayout, bioLayout;
    private TextInputEditText nameInput, addressInput, bioInput;
    private ImageView avatarPhoto;
    private TextView avatarInitial;
    private Button saveButton;
    private ProgressBar progressBar, avatarProgress;

    private String pictureUrl;
    private boolean uploadingAvatar = false;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) uploadAvatar(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        nameLayout = findViewById(R.id.nameLayout);
        addressLayout = findViewById(R.id.addressLayout);
        bioLayout = findViewById(R.id.bioLayout);
        nameInput = findViewById(R.id.nameInput);
        addressInput = findViewById(R.id.addressInput);
        bioInput = findViewById(R.id.bioInput);
        avatarPhoto = findViewById(R.id.avatarPhoto);
        avatarInitial = findViewById(R.id.avatarInitial);
        saveButton = findViewById(R.id.saveButton);
        progressBar = findViewById(R.id.progressBar);
        avatarProgress = findViewById(R.id.avatarProgress);

        findViewById(R.id.avatarContainer).setOnClickListener(v -> choosePhoto());
        findViewById(R.id.changePhotoLink).setOnClickListener(v -> choosePhoto());
        findViewById(R.id.cancelLink).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> attemptSave());

        if (savedInstanceState != null) {
            // The text fields restore themselves; only the photo needs to be kept.
            pictureUrl = savedInstanceState.getString(EXTRA_PICTURE);
            Avatars.bind(avatarPhoto, avatarInitial, textOf(nameInput), pictureUrl);
        } else if (getIntent().hasExtra(EXTRA_NAME)) {
            fill(getIntent().getStringExtra(EXTRA_NAME),
                    getIntent().getStringExtra(EXTRA_ADDRESS),
                    getIntent().getStringExtra(EXTRA_BIO),
                    getIntent().getStringExtra(EXTRA_PICTURE));
        } else {
            loadProfile();
        }

        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_FOCUS_BIO, false)) {
            focusBio();
        }
    }

    /** Puts the cursor at the end of the bio and opens the keyboard. */
    private void focusBio() {
        bioInput.requestFocus();
        bioInput.setSelection(bioInput.length());
        WindowCompat.getInsetsController(getWindow(), bioInput).show(WindowInsetsCompat.Type.ime());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_PICTURE, pictureUrl);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageExecutor.shutdownNow();
    }

    private void fill(String name, String address, String bio, String picture) {
        nameInput.setText(name);
        addressInput.setText(address);
        bioInput.setText(bio);
        pictureUrl = picture;
        Avatars.bind(avatarPhoto, avatarInitial, name, picture);
    }

    /** Used when the screen is opened without the current values, e.g. before the profile loaded. */
    private void loadProfile() {
        setLoading(true);
        ApiClient.getService(this)
                .getProfile()
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<UserResponse> call,
                                           @NonNull Response<UserResponse> response) {
                        if (isDestroyed()) return;
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            UserResponse user = response.body();
                            fill(user.name, user.address, user.bio, user.profilePicture);
                        } else if (response.code() == 401) {
                            Session.expire(EditProfileActivity.this);
                        } else {
                            Toast.makeText(EditProfileActivity.this,
                                    R.string.profile_load_failed, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        setLoading(false);
                        Toast.makeText(EditProfileActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private void attemptSave() {
        clearErrors();

        String name = textOf(nameInput);
        String address = textOf(addressInput);
        String bio = textOf(bioInput);

        boolean valid = true;
        if (name.isEmpty()) { nameLayout.setError(getString(R.string.error_name_required)); valid = false; }
        else if (name.length() > MAX_NAME) { nameLayout.setError(getString(R.string.error_name_too_long)); valid = false; }
        if (address.isEmpty()) { addressLayout.setError(getString(R.string.error_city_required)); valid = false; }
        else if (address.length() > MAX_CITY) { addressLayout.setError(getString(R.string.error_city_too_long)); valid = false; }
        if (bio.length() > MAX_BIO) { bioLayout.setError(getString(R.string.error_bio_too_long)); valid = false; }
        if (!valid) return;

        setLoading(true);

        // An empty bio is sent as null so the profile shows its placeholder.
        UserUpdateRequest request = new UserUpdateRequest(name, bio.isEmpty() ? null : bio, address);

        ApiClient.getService(this)
                .updateProfile(request)
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<UserResponse> call,
                                           @NonNull Response<UserResponse> response) {
                        if (isDestroyed()) return;
                        setLoading(false);
                        if (response.isSuccessful()) {
                            Toast.makeText(EditProfileActivity.this,
                                    R.string.edit_profile_saved, Toast.LENGTH_SHORT).show();
                            finish();
                        } else if (response.code() == 401) {
                            Session.expire(EditProfileActivity.this);
                        } else {
                            showServerErrors(ApiClient.parseError(response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        setLoading(false);
                        Toast.makeText(EditProfileActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showServerErrors(ErrorResponse error) {
        if (error != null && error.errors != null && !error.errors.isEmpty()) {
            for (Map.Entry<String, String> entry : error.errors.entrySet()) {
                TextInputLayout layout = layoutFor(entry.getKey());
                if (layout != null) layout.setError(entry.getValue());
            }
            return;
        }
        String message = error != null && error.message != null
                ? error.message : getString(R.string.edit_profile_failed);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private TextInputLayout layoutFor(String field) {
        switch (field) {
            case "name": return nameLayout;
            case "address": return addressLayout;
            case "bio": return bioLayout;
            default: return null;
        }
    }

    private void choosePhoto() {
        if (uploadingAvatar) return;
        pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /** Shows the picked photo right away, compresses it off the main thread, then uploads it. */
    private void uploadAvatar(Uri uri) {
        setUploading(true);
        Glide.with(this).load(uri).circleCrop().into(avatarPhoto);

        imageExecutor.execute(() -> {
            byte[] jpeg;
            try {
                jpeg = ImageCompressor.toJpeg(getContentResolver(), uri);
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    avatarFailed(getString(R.string.avatar_unreadable));
                });
                return;
            }
            runOnUiThread(() -> {
                if (!isDestroyed()) sendAvatar(jpeg);
            });
        });
    }

    private void sendAvatar(byte[] jpeg) {
        RequestBody body = RequestBody.create(jpeg, MediaType.get("image/jpeg"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", "avatar.jpg", body);

        ApiClient.getService(this)
                .uploadAvatar(part)
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<UserResponse> call,
                                           @NonNull Response<UserResponse> response) {
                        if (isDestroyed()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            setUploading(false);
                            pictureUrl = response.body().profilePicture;
                            Avatars.bind(avatarPhoto, avatarInitial, textOf(nameInput), pictureUrl);
                            Toast.makeText(EditProfileActivity.this,
                                    R.string.avatar_uploaded, Toast.LENGTH_SHORT).show();
                        } else if (response.code() == 401) {
                            Session.expire(EditProfileActivity.this);
                        } else {
                            ErrorResponse error = ApiClient.parseError(response);
                            avatarFailed(error != null && error.message != null
                                    ? error.message : getString(R.string.avatar_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        avatarFailed(getString(R.string.error_network));
                    }
                });
    }

    /** Puts the previous photo back and explains what went wrong. */
    private void avatarFailed(String message) {
        setUploading(false);
        Avatars.bind(avatarPhoto, avatarInitial, textOf(nameInput), pictureUrl);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void clearErrors() {
        nameLayout.setError(null);
        addressLayout.setError(null);
        bioLayout.setError(null);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!loading);
    }

    private void setUploading(boolean uploading) {
        uploadingAvatar = uploading;
        avatarProgress.setVisibility(uploading ? View.VISIBLE : View.GONE);
        avatarPhoto.setAlpha(uploading ? 0.5f : 1f);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}

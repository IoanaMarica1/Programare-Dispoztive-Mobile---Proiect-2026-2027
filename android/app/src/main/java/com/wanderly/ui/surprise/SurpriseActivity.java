package com.wanderly.ui.surprise;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.CancellationSignal;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.CheckInRequest;
import com.wanderly.data.remote.dto.SurprisePlaceResponse;
import com.wanderly.data.remote.dto.SurpriseRequest;
import com.wanderly.data.remote.dto.VisitResultResponse;
import com.wanderly.ui.common.Avatars;
import com.wanderly.ui.common.Formats;
import com.wanderly.ui.common.Session;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Story E1. One tap on a mood gives the best place within 2 km that fits it and is open now: why it
 * matches, how far it is, a button to walk there and, once there, a check-in that earns points.
 */
public class SurpriseActivity extends AppCompatActivity {

    private static final String STATE_PLACE = "place";
    private static final String STATE_SHOWN = "shown";
    private static final String STATE_MOOD = "mood";
    private static final String STATE_CHECKED_IN = "checkedIn";

    /** What the screen shows. The mood grid stays visible under a message, so a dead end leads to another mood. */
    private enum Screen { PICK, LOADING, MESSAGE, RESULT }

    /** Why a message is up, so coming back from the settings app can carry on where it stopped. */
    private enum Reason { NONE, PERMISSION_ASK, PERMISSION_BLOCKED, SERVICES_OFF, OTHER }

    private View moodPanel, loadingPanel, messagePanel, resultPanel, addressRow, subtitleText;
    private TextView messageTitle, messageBody;
    private MaterialButton messageButton, takeMeThereButton, anotherButton, checkInButton;
    private ImageView placePhoto;
    private TextView placeInitial, placeCategory, placeName, placeWhy, placeDistance, placeOpen, placeAddress;

    private final Gson gson = new Gson();
    private Mood mood;
    private SurprisePlaceResponse place;
    /** Places already shown for this mood, so "Another one" never repeats. */
    private final Set<String> shown = new LinkedHashSet<>();
    private boolean checkedIn;

    private Reason reason = Reason.NONE;
    /** What to do once the traveler has fixed the permission or the location setting. */
    private Runnable resume;
    /** True while a position or a server answer is awaited: taps are ignored so nothing is sent twice. */
    private boolean busy;
    private Call<?> activeCall;
    private CancellationSignal activeLocation;

    private final ActivityResultLauncher<String[]> permissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    /** Only for check-in: asks to upgrade approximate location to precise, then goes on with the check-in. */
    private final ActivityResultLauncher<String> finePermissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) checkIn();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_surprise);

        moodPanel = findViewById(R.id.moodPanel);
        subtitleText = findViewById(R.id.subtitleText);
        loadingPanel = findViewById(R.id.loadingPanel);
        messagePanel = findViewById(R.id.messagePanel);
        resultPanel = findViewById(R.id.resultPanel);
        addressRow = findViewById(R.id.placeAddressRow);
        messageTitle = findViewById(R.id.messageTitle);
        messageBody = findViewById(R.id.messageBody);
        messageButton = findViewById(R.id.messageButton);
        takeMeThereButton = findViewById(R.id.takeMeThereButton);
        anotherButton = findViewById(R.id.anotherButton);
        checkInButton = findViewById(R.id.checkInButton);
        placePhoto = findViewById(R.id.placePhoto);
        placeInitial = findViewById(R.id.placeInitial);
        placeCategory = findViewById(R.id.placeCategory);
        placeName = findViewById(R.id.placeName);
        placeWhy = findViewById(R.id.placeWhy);
        placeDistance = findViewById(R.id.placeDistance);
        placeOpen = findViewById(R.id.placeOpen);
        placeAddress = findViewById(R.id.placeAddress);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        takeMeThereButton.setOnClickListener(v -> takeMeThere());
        anotherButton.setOnClickListener(v -> another());
        checkInButton.setOnClickListener(v -> checkIn());
        findViewById(R.id.changeMoodButton).setOnClickListener(v -> changeMood());

        buildMoodButtons();
        restore(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (place != null) {
            out.putString(STATE_PLACE, gson.toJson(place));
            out.putStringArrayList(STATE_SHOWN, new ArrayList<>(shown));
            out.putString(STATE_MOOD, mood == null ? null : mood.name());
            out.putBoolean(STATE_CHECKED_IN, checkedIn);
        }
    }

    /** After a rotation the card comes back as it was, with the same places already skipped. */
    private void restore(@Nullable Bundle state) {
        String json = state == null ? null : state.getString(STATE_PLACE);
        SurprisePlaceResponse saved = json == null ? null : gson.fromJson(json, SurprisePlaceResponse.class);
        if (saved == null) {
            show(Screen.PICK);
            return;
        }
        mood = Mood.fromName(state.getString(STATE_MOOD));
        if (mood == null) {   // saved by another version of the app: nothing sensible to continue from
            show(Screen.PICK);
            return;
        }
        ArrayList<String> ids = state.getStringArrayList(STATE_SHOWN);
        if (ids != null) shown.addAll(ids);
        checkedIn = state.getBoolean(STATE_CHECKED_IN);
        showPlace(saved);
    }

    // Back from the settings app (or the permission dialog): carry on if what was missing is now there.
    @Override
    protected void onResume() {
        super.onResume();
        if (busy || resume == null) return;
        boolean permissionAnswered = (reason == Reason.PERMISSION_ASK || reason == Reason.PERMISSION_BLOCKED)
                && LocationFinder.hasAnyPermission(this);
        if (permissionAnswered || reason == Reason.SERVICES_OFF) {
            Runnable action = resume;
            resume = null;
            action.run();   // asks again if location is still off
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelWork();
    }

    // ------------------------------------------------------------------ mood picker

    private void buildMoodButtons() {
        GridLayout grid = findViewById(R.id.moodGrid);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Mood m : Mood.values()) {
            MaterialButton button = (MaterialButton) inflater.inflate(R.layout.item_mood_button, grid, false);
            button.setText(m.label);
            button.setOnClickListener(v -> chooseMood(m));
            grid.addView(button);
        }
    }

    private void chooseMood(Mood chosen) {
        if (busy) return;
        mood = chosen;
        shown.clear();
        place = null;
        checkedIn = false;
        suggest(false);
    }

    private void changeMood() {
        cancelWork();
        place = null;
        shown.clear();
        resume = null;
        show(Screen.PICK);
    }

    private void another() {
        if (busy || place == null || !place.moreAvailable) return;
        suggest(true);
    }

    // ------------------------------------------------------------------ the suggestion

    /** {@code another}: the current card stays on screen if getting the next one fails. */
    private void suggest(boolean another) {
        withLocation(false, another, location -> request(location, another));
    }

    private void request(Location location, boolean another) {
        SurpriseRequest body = new SurpriseRequest(mood.name(), location.getLatitude(), location.getLongitude(),
                TimeZone.getDefault().getID(), recentlyShown());

        Call<SurprisePlaceResponse> call = ApiClient.getService(this).surprise(body);
        activeCall = call;
        call.enqueue(new Callback<SurprisePlaceResponse>() {
            @Override
            public void onResponse(@NonNull Call<SurprisePlaceResponse> c, @NonNull Response<SurprisePlaceResponse> r) {
                if (isDestroyed()) return;
                busy = false;
                if (r.isSuccessful() && r.body() != null) {
                    shown.add(r.body().id);
                    checkedIn = false;
                    showPlace(r.body());
                } else if (r.code() == 401) {
                    Session.expire(SurpriseActivity.this);
                } else if (r.code() == 404) {
                    // Nothing (more) open that fits is not a failure: no "try again", just another mood.
                    String text = ApiClient.errorMessage(r, getString(R.string.surprise_error_generic));
                    if (another && place != null) {
                        place.moreAvailable = false;
                        showPlace(place);
                        Toast.makeText(SurpriseActivity.this, text, Toast.LENGTH_LONG).show();
                    } else {
                        showMessage(Reason.OTHER, R.string.surprise_nothing_title, text, 0, null);
                    }
                } else {
                    requestFailed(another, ApiClient.errorMessage(r, getString(R.string.surprise_error_generic)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<SurprisePlaceResponse> c, @NonNull Throwable t) {
                if (isDestroyed() || c.isCanceled()) return;
                busy = false;
                requestFailed(another, getString(R.string.error_network));
            }
        });
    }

    private void requestFailed(boolean another, String text) {
        if (another && place != null) {
            showPlace(place);   // back to the card, buttons enabled again
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        } else {
            showMessage(Reason.OTHER, R.string.surprise_error_title, text, R.string.surprise_retry, () -> suggest(false));
        }
    }

    /** The server skips at most this many places; older ones can only come back if the area has more than that. */
    private static final int MAX_EXCLUDED = 50;

    private ArrayList<String> recentlyShown() {
        ArrayList<String> all = new ArrayList<>(shown);
        return new ArrayList<>(all.subList(Math.max(0, all.size() - MAX_EXCLUDED), all.size()));
    }

    private void showPlace(SurprisePlaceResponse p) {
        place = p;
        placeCategory.setText(p.category);
        placeName.setText(p.name);
        placeWhy.setText(p.why);
        placeDistance.setText(getString(R.string.surprise_distance_walk, Formats.distance(p.distanceMeters), p.walkingMinutes));
        placeOpen.setText(p.openNote);
        placeOpen.setVisibility(p.openNote == null ? View.GONE : View.VISIBLE);
        addressRow.setVisibility(p.address == null || p.address.isEmpty() ? View.GONE : View.VISIBLE);
        placeAddress.setText(p.address);

        // The initial is the placeholder: it shows while the photo loads and when there is none.
        placeInitial.setText(Avatars.initialOf(p.name));
        placePhoto.setContentDescription(getString(R.string.surprise_photo_description, p.name));
        if (p.photoUrl == null || p.photoUrl.isEmpty()) {
            Glide.with(placePhoto).clear(placePhoto);
            placePhoto.setImageDrawable(null);
        } else {
            Glide.with(placePhoto).load(p.photoUrl).centerCrop().into(placePhoto);
        }

        setCardWorking(false, false);
        show(Screen.RESULT);
    }

    /** The card's buttons while something is going on: nothing can be tapped twice. */
    private void setCardWorking(boolean working, boolean checkingIn) {
        busy = working;
        takeMeThereButton.setEnabled(!working);
        boolean more = place != null && place.moreAvailable;
        anotherButton.setEnabled(!working && more);
        anotherButton.setText(more ? R.string.surprise_another : R.string.surprise_no_more);
        checkInButton.setEnabled(!working && !checkedIn);
        checkInButton.setText(checkedIn ? R.string.surprise_checked_in
                : working && checkingIn ? R.string.surprise_checking_in : R.string.surprise_check_in);
    }

    // ------------------------------------------------------------------ take me there

    /** Walking directions in Google Maps if it is installed, else any maps app, else the browser. */
    private void takeMeThere() {
        if (place == null) return;
        Intent maps = new Intent(Intent.ACTION_VIEW,
                Uri.parse(SurpriseLinks.walkingNavigation(place.latitude, place.longitude)))
                .setPackage("com.google.android.apps.maps");
        Intent geo = new Intent(Intent.ACTION_VIEW,
                Uri.parse(SurpriseLinks.geo(place.latitude, place.longitude, place.name)));
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(SurpriseLinks.web(place.latitude, place.longitude)));
        for (Intent intent : new Intent[]{maps, geo, web}) {
            try {
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Try the next way.
            }
        }
        Toast.makeText(this, R.string.surprise_error_generic, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ check-in

    private void checkIn() {
        if (place == null || busy || checkedIn) return;
        if (!LocationFinder.hasFinePermission(this)) {
            // Approximate location can be a kilometre off: it would never pass the server's 150 m check.
            // Android 12+ lets the traveler upgrade from approximate to precise with one more question.
            Toast.makeText(this, R.string.surprise_check_in_precise, Toast.LENGTH_LONG).show();
            finePermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        SurprisePlaceResponse target = place;
        withLocation(true, true, location -> sendCheckIn(target, location));
    }

    private void sendCheckIn(SurprisePlaceResponse target, Location location) {
        Call<VisitResultResponse> call = ApiClient.getService(this)
                .checkIn(new CheckInRequest(target.id, location.getLatitude(), location.getLongitude()));
        activeCall = call;
        call.enqueue(new Callback<VisitResultResponse>() {
            @Override
            public void onResponse(@NonNull Call<VisitResultResponse> c, @NonNull Response<VisitResultResponse> r) {
                if (isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null) {
                    checkedIn = true;
                    setCardWorking(false, false);
                    showCheckInResult(target, r.body());
                } else if (r.code() == 401) {
                    Session.expire(SurpriseActivity.this);
                } else {
                    setCardWorking(false, false);
                    // "You are 380 m from ... Check in when you are within 150 m." and the other server reasons.
                    Toast.makeText(SurpriseActivity.this,
                            ApiClient.errorMessage(r, getString(R.string.surprise_check_in_failed)), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<VisitResultResponse> c, @NonNull Throwable t) {
                if (isDestroyed() || c.isCanceled()) return;
                setCardWorking(false, false);
                Toast.makeText(SurpriseActivity.this, R.string.error_network, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showCheckInResult(SurprisePlaceResponse target, VisitResultResponse result) {
        String body = result.firstVisit
                ? getResources().getQuantityString(R.plurals.surprise_check_in_earned, result.pointsAwarded,
                        result.pointsAwarded, result.totalPoints)
                : getString(R.string.surprise_check_in_repeat, target.name);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.surprise_check_in_done_title, target.name))
                .setMessage(body)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    // ------------------------------------------------------------------ location and permission

    private static String[] permissions() {
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    private interface WithLocation {
        void run(Location location);
    }

    /**
     * Runs {@code next} with the phone's position, asking for the permission first if needed. Everything
     * that can go wrong (denied, blocked, location off, no signal) ends in a message saying how to fix it.
     *
     * @param onCard true when a card is on screen and must stay there (a failure is then a toast)
     */
    private void withLocation(boolean precise, boolean onCard, WithLocation next) {
        if (!LocationFinder.hasAnyPermission(this)) {
            resume = () -> withLocation(precise, onCard, next);
            askForPermission();
            return;
        }
        cancelWork();
        if (onCard && place != null) {
            setCardWorking(true, precise);
        } else {
            busy = true;
            show(Screen.LOADING);
        }

        activeLocation = LocationFinder.find(this, precise, new LocationFinder.Callback() {
            @Override
            public void onLocation(@NonNull Location location) {
                if (isDestroyed()) return;
                next.run(location);
            }

            @Override
            public void onFailure(@NonNull LocationFinder.Failure failure) {
                if (isDestroyed()) return;
                locationFailed(failure, onCard, () -> withLocation(precise, onCard, next));
            }
        });
    }

    private void locationFailed(LocationFinder.Failure failure, boolean onCard, Runnable retry) {
        boolean off = failure == LocationFinder.Failure.SERVICES_OFF;
        if (onCard && place != null) {
            setCardWorking(false, false);
            Toast.makeText(this, off ? R.string.surprise_services_off_body : R.string.surprise_unavailable_body,
                    Toast.LENGTH_LONG).show();
        } else if (off) {
            resume = retry;
            showMessage(Reason.SERVICES_OFF, R.string.surprise_services_off_title,
                    getString(R.string.surprise_services_off_body), R.string.surprise_open_location_settings,
                    () -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        } else {
            showMessage(Reason.OTHER, R.string.surprise_unavailable_title,
                    getString(R.string.surprise_unavailable_body), R.string.surprise_retry, retry);
        }
    }

    /** Explains why, and shows Android's dialog right away to save a tap. */
    private void askForPermission() {
        showMessage(Reason.PERMISSION_ASK, R.string.surprise_permission_title,
                getString(R.string.surprise_permission_body), R.string.surprise_permission_allow,
                () -> permissionRequest.launch(permissions()));
        permissionRequest.launch(permissions());
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        if (LocationFinder.hasAnyPermission(this)) {
            Runnable action = resume;
            resume = null;
            if (action != null) action.run();
            return;
        }
        // Denied. Once Android no longer shows its dialog, the app settings are the only way on.
        boolean canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (canAskAgain) {
            showMessage(Reason.PERMISSION_ASK, R.string.surprise_permission_title,
                    getString(R.string.surprise_permission_body), R.string.surprise_permission_allow,
                    () -> permissionRequest.launch(permissions()));
        } else {
            showMessage(Reason.PERMISSION_BLOCKED, R.string.surprise_permission_denied_title,
                    getString(R.string.surprise_permission_denied_body), R.string.surprise_open_settings,
                    () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null))));
        }
    }

    // ------------------------------------------------------------------ screens

    private void showMessage(Reason why, @StringRes int title, String body,
                             @StringRes int buttonLabel, @Nullable Runnable action) {
        busy = false;
        messageTitle.setText(title);
        messageBody.setText(body);
        messageButton.setVisibility(action == null ? View.GONE : View.VISIBLE);
        if (action != null) {
            messageButton.setText(buttonLabel);
            messageButton.setOnClickListener(v -> action.run());
        }
        show(Screen.MESSAGE);
        reason = why;   // after show(), which clears it for the other screens
    }

    private void show(Screen next) {
        if (next != Screen.MESSAGE) reason = Reason.NONE;
        moodPanel.setVisibility(next == Screen.PICK || next == Screen.MESSAGE ? View.VISIBLE : View.GONE);
        subtitleText.setVisibility(next == Screen.PICK ? View.VISIBLE : View.GONE);
        loadingPanel.setVisibility(next == Screen.LOADING ? View.VISIBLE : View.GONE);
        messagePanel.setVisibility(next == Screen.MESSAGE ? View.VISIBLE : View.GONE);
        resultPanel.setVisibility(next == Screen.RESULT ? View.VISIBLE : View.GONE);
    }

    private void cancelWork() {
        if (activeCall != null) activeCall.cancel();
        if (activeLocation != null) activeLocation.cancel();
        activeCall = null;
        activeLocation = null;
        busy = false;
    }
}

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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.CancellationSignal;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.CheckInRequest;
import com.wanderly.data.remote.dto.SurprisePlaceResponse;
import com.wanderly.data.remote.dto.SurpriseRequest;
import com.wanderly.data.remote.dto.VisitResultResponse;
import com.wanderly.ui.common.Session;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Story E1. One tap on a mood gives a few places within 2 km that fit it and are open now, as cards to swipe
 * through: why each one matches, how far it is, a button to walk there and, once there, a check-in that
 * earns points.
 */
public class SurpriseActivity extends AppCompatActivity implements PlaceCardAdapter.Listener {

    private static final String STATE_PLACES = "places";
    private static final String STATE_PAGE = "page";
    private static final String STATE_MOOD = "mood";
    private static final String STATE_CHECKED_IN = "checkedIn";
    private static final Type PLACE_LIST = new TypeToken<List<SurprisePlaceResponse>>() {}.getType();

    /** What the screen shows. The mood grid stays visible under a message, so a dead end leads to another mood. */
    private enum Screen { PICK, LOADING, MESSAGE, RESULT }

    /** Why a message is up, so coming back from the settings app can carry on where it stopped. */
    private enum Reason { NONE, PERMISSION_ASK, PERMISSION_BLOCKED, SERVICES_OFF, OTHER }

    private View moodPanel, loadingPanel, messagePanel, resultPanel, subtitleText, swipeHint;
    private TextView messageTitle, messageBody;
    private MaterialButton messageButton;
    private LinearLayout dotsRow;
    private ViewPager2 placePager;
    private PlaceCardAdapter adapter;

    private final Gson gson = new Gson();
    private Mood mood;
    private final List<SurprisePlaceResponse> places = new ArrayList<>();
    private final Set<String> checkedIn = new LinkedHashSet<>();

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
                if (granted) onCheckIn(currentPlace());
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
        swipeHint = findViewById(R.id.swipeHint);
        dotsRow = findViewById(R.id.dotsRow);
        placePager = findViewById(R.id.placePager);
        messageTitle = findViewById(R.id.messageTitle);
        messageBody = findViewById(R.id.messageBody);
        messageButton = findViewById(R.id.messageButton);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.changeMoodButton).setOnClickListener(v -> changeMood());

        setUpPager();
        buildMoodTiles();
        restore(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (!places.isEmpty() && mood != null) {
            out.putString(STATE_PLACES, gson.toJson(places, PLACE_LIST));
            out.putInt(STATE_PAGE, placePager.getCurrentItem());
            out.putString(STATE_MOOD, mood.name());
            out.putStringArrayList(STATE_CHECKED_IN, new ArrayList<>(checkedIn));
        }
    }

    /** After a rotation the cards come back as they were, on the same page. */
    private void restore(@Nullable Bundle state) {
        String json = state == null ? null : state.getString(STATE_PLACES);
        List<SurprisePlaceResponse> saved = json == null ? null : gson.fromJson(json, PLACE_LIST);
        mood = state == null ? null : Mood.fromName(state.getString(STATE_MOOD));
        // A mood the app no longer knows (saved by another version): nothing sensible to continue from.
        if (saved == null || saved.isEmpty() || mood == null) {
            show(Screen.PICK);
            return;
        }
        ArrayList<String> ids = state.getStringArrayList(STATE_CHECKED_IN);
        checkedIn.clear();
        if (ids != null) checkedIn.addAll(ids);
        showPlaces(saved, state.getInt(STATE_PAGE, 0));
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

    private void buildMoodTiles() {
        GridLayout grid = findViewById(R.id.moodGrid);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Mood m : Mood.values()) {
            View tile = inflater.inflate(R.layout.item_mood_tile, grid, false);
            ((TextView) tile.findViewById(R.id.moodEmoji)).setText(m.emoji);
            ((TextView) tile.findViewById(R.id.moodLabel)).setText(m.label);
            tile.setContentDescription(getString(m.label));
            tile.setOnClickListener(v -> chooseMood(m));
            grid.addView(tile);
        }
    }

    private void chooseMood(Mood chosen) {
        if (busy) return;
        mood = chosen;
        places.clear();
        checkedIn.clear();
        suggest();
    }

    private void changeMood() {
        cancelWork();
        places.clear();
        checkedIn.clear();
        resume = null;
        show(Screen.PICK);
    }

    // ------------------------------------------------------------------ the suggestions

    private void suggest() {
        withLocation(false, false, this::request);
    }

    private void request(Location location) {
        SurpriseRequest body = new SurpriseRequest(mood.name(), location.getLatitude(), location.getLongitude(),
                TimeZone.getDefault().getID(), null);

        Call<List<SurprisePlaceResponse>> call = ApiClient.getService(this).surprise(body);
        activeCall = call;
        call.enqueue(new Callback<List<SurprisePlaceResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SurprisePlaceResponse>> c,
                                   @NonNull Response<List<SurprisePlaceResponse>> r) {
                if (isDestroyed()) return;
                busy = false;
                if (r.isSuccessful() && r.body() != null && !r.body().isEmpty()) {
                    showPlaces(r.body(), 0);
                } else if (r.code() == 401) {
                    Session.expire(SurpriseActivity.this);
                } else if (r.isSuccessful() || r.code() == 404) {
                    // Nothing open that fits is not a failure: no "try again", just another mood.
                    String text = r.isSuccessful() ? getString(R.string.surprise_error_generic)
                            : ApiClient.errorMessage(r, getString(R.string.surprise_error_generic));
                    showMessage(Reason.OTHER, R.string.surprise_nothing_title, text, 0, null);
                } else {
                    requestFailed(ApiClient.errorMessage(r, getString(R.string.surprise_error_generic)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<SurprisePlaceResponse>> c, @NonNull Throwable t) {
                if (isDestroyed() || c.isCanceled()) return;
                busy = false;
                requestFailed(getString(R.string.error_network));
            }
        });
    }

    private void requestFailed(String text) {
        showMessage(Reason.OTHER, R.string.surprise_error_title, text, R.string.surprise_retry, this::suggest);
    }

    private void setUpPager() {
        adapter = new PlaceCardAdapter(this);
        placePager.setAdapter(adapter);
        placePager.setOffscreenPageLimit(1);   // the neighbours are drawn already, so they peek in at the edges

        // A little gap between cards, and the card that is not in the middle sits slightly smaller.
        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(getResources().getDimensionPixelSize(R.dimen.pager_gap)));
        transformer.addTransformer((page, position) -> {
            float closeness = 1f - Math.min(1f, Math.abs(position));
            page.setScaleY(0.94f + 0.06f * closeness);
        });
        placePager.setPageTransformer(transformer);
        placePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                selectDot(position);
            }
        });
    }

    private void showPlaces(List<SurprisePlaceResponse> list, int page) {
        places.clear();
        places.addAll(list);
        adapter.submit(places, checkedIn);
        int start = Math.max(0, Math.min(page, places.size() - 1));
        placePager.setCurrentItem(start, false);

        boolean several = places.size() > 1;
        swipeHint.setVisibility(several ? View.VISIBLE : View.GONE);
        dotsRow.setVisibility(several ? View.VISIBLE : View.GONE);
        buildDots(places.size());
        selectDot(start);

        setCardWorking(false, null);
        show(Screen.RESULT);
    }

    /** The place whose card is on screen, or null when there is none. */
    @Nullable
    private SurprisePlaceResponse currentPlace() {
        int position = placePager.getCurrentItem();
        return position >= 0 && position < places.size() ? places.get(position) : null;
    }

    private void buildDots(int count) {
        dotsRow.removeAllViews();
        int size = getResources().getDimensionPixelSize(R.dimen.space_s);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xs);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_pager_dot);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(gap, 0, gap, 0);
            dotsRow.addView(dot, params);
        }
    }

    private void selectDot(int position) {
        for (int i = 0; i < dotsRow.getChildCount(); i++) {
            dotsRow.getChildAt(i).setSelected(i == position);
        }
    }

    /** The cards' buttons while something is going on: nothing can be tapped twice. */
    private void setCardWorking(boolean working, @Nullable String checkingInId) {
        busy = working;
        adapter.setWorking(working, checkingInId);
    }

    // ------------------------------------------------------------------ take me there

    /** Walking directions in Google Maps if it is installed, else any maps app, else the browser. */
    @Override
    public void onTakeMeThere(SurprisePlaceResponse place) {
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

    @Override
    public void onCheckIn(SurprisePlaceResponse place) {
        if (place == null || busy || checkedIn.contains(place.id)) return;
        if (!LocationFinder.hasFinePermission(this)) {
            // Approximate location can be a kilometre off: it would never pass the server's 150 m check.
            // Android 12+ lets the traveler upgrade from approximate to precise with one more question.
            Toast.makeText(this, R.string.surprise_check_in_precise, Toast.LENGTH_LONG).show();
            finePermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        withLocation(true, true, location -> sendCheckIn(place, location));
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
                    checkedIn.add(target.id);
                    adapter.markCheckedIn(target.id);
                    setCardWorking(false, null);
                    showCheckInResult(target, r.body());
                } else if (r.code() == 401) {
                    Session.expire(SurpriseActivity.this);
                } else {
                    setCardWorking(false, null);
                    // "You are 380 m from ... Check in when you are within 150 m." and the other server reasons.
                    Toast.makeText(SurpriseActivity.this,
                            ApiClient.errorMessage(r, getString(R.string.surprise_check_in_failed)), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<VisitResultResponse> c, @NonNull Throwable t) {
                if (isDestroyed() || c.isCanceled()) return;
                setCardWorking(false, null);
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
     * @param onCard true when the cards are on screen and must stay there (a failure is then a toast)
     */
    private void withLocation(boolean precise, boolean onCard, WithLocation next) {
        if (!LocationFinder.hasAnyPermission(this)) {
            resume = () -> withLocation(precise, onCard, next);
            askForPermission();
            return;
        }
        cancelWork();
        if (onCard && !places.isEmpty()) {
            SurprisePlaceResponse target = currentPlace();
            setCardWorking(true, precise && target != null ? target.id : null);
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
        if (onCard && !places.isEmpty()) {
            setCardWorking(false, null);
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

package com.wanderly.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;
import com.wanderly.R;
import com.wanderly.data.local.TokenManager;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.CountryHistoryResponse;
import com.wanderly.data.remote.dto.PageResponse;
import com.wanderly.data.remote.dto.PreferencesResponse;
import com.wanderly.data.remote.dto.TripResponse;
import com.wanderly.data.remote.dto.TripSummaryResponse;
import com.wanderly.data.remote.dto.UserResponse;
import com.wanderly.ui.adventure.PlanActivity;
import com.wanderly.ui.auth.LoginActivity;
import com.wanderly.ui.common.Avatars;
import com.wanderly.ui.common.Formats;
import com.wanderly.ui.common.Session;
import com.wanderly.ui.preferences.AdventureActivity;
import com.wanderly.ui.preferences.PreferencesActivity;
import com.wanderly.ui.preferences.PreferencesWizard;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 10;

    private ImageView avatarPhoto;
    private TextView avatarInitial, nameText, cityText, rankBadge, bioText;
    private TextView pointsText, countriesCountText, nextRankText, pointsToGoText;
    private LinearProgressIndicator rankProgress;
    private ChipGroup preferencesChips;
    private View preferencesEmpty, editPreferencesLink, historyEmpty;
    private LinearLayout countriesList, adventuresList;
    private View adventuresEmpty;
    private Button showMoreCountriesButton, showMoreAdventuresButton;
    private ProgressBar progressBar, historyProgress;

    private UserResponse user;
    private int nextCountriesPage = 0;
    private boolean loadingCountries = false;
    private int nextAdventuresPage = 0;
    private boolean loadingAdventures = false;
    private boolean openingAdventure = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        avatarPhoto = findViewById(R.id.avatarPhoto);
        avatarInitial = findViewById(R.id.avatarInitial);
        nameText = findViewById(R.id.nameText);
        cityText = findViewById(R.id.cityText);
        rankBadge = findViewById(R.id.rankBadge);
        bioText = findViewById(R.id.bioText);
        pointsText = findViewById(R.id.pointsText);
        countriesCountText = findViewById(R.id.countriesCountText);
        nextRankText = findViewById(R.id.nextRankText);
        pointsToGoText = findViewById(R.id.pointsToGoText);
        rankProgress = findViewById(R.id.rankProgress);
        preferencesChips = findViewById(R.id.preferencesChips);
        preferencesEmpty = findViewById(R.id.preferencesEmpty);
        editPreferencesLink = findViewById(R.id.editPreferencesLink);
        historyEmpty = findViewById(R.id.historyEmpty);
        countriesList = findViewById(R.id.countriesList);
        showMoreCountriesButton = findViewById(R.id.showMoreCountriesButton);
        adventuresList = findViewById(R.id.adventuresList);
        adventuresEmpty = findViewById(R.id.adventuresEmpty);
        showMoreAdventuresButton = findViewById(R.id.showMoreAdventuresButton);
        progressBar = findViewById(R.id.progressBar);
        historyProgress = findViewById(R.id.historyProgress);

        findViewById(R.id.editProfileButton).setOnClickListener(v -> openEditProfile(false));
        findViewById(R.id.aboutCard).setOnClickListener(v -> openEditProfile(true));
        findViewById(R.id.editBioLink).setOnClickListener(v -> openEditProfile(true));
        findViewById(R.id.setPreferencesButton).setOnClickListener(v -> openPreferences());
        findViewById(R.id.startFirstAdventureButton).setOnClickListener(v ->
                startActivity(new Intent(this, AdventureActivity.class)));
        findViewById(R.id.newAdventureLink).setOnClickListener(v ->
                startActivity(new Intent(this, AdventureActivity.class)));
        showMoreAdventuresButton.setOnClickListener(v -> loadAdventures(false));
        editPreferencesLink.setOnClickListener(v -> openPreferences());
        showMoreCountriesButton.setOnClickListener(v -> loadCountries(false));
        findViewById(R.id.logoutButton).setOnClickListener(v -> logout());
    }

    // Reloads after coming back from the edit screen or the wizard.
    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
        loadPreferences();
        loadAdventures(true);
        loadCountries(true);
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
                            Session.expire(ProfileActivity.this);
                        } else {
                            Toast.makeText(ProfileActivity.this,
                                    R.string.profile_load_failed, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ProfileActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showUser(UserResponse user) {
        this.user = user;
        String name = user.name != null ? user.name.trim() : "";
        int points = user.points != null ? user.points : 0;

        Avatars.bind(avatarPhoto, avatarInitial, name, user.profilePicture);
        nameText.setText(name);
        cityText.setText(user.address);

        boolean hasBio = user.bio != null && !user.bio.trim().isEmpty();
        bioText.setText(hasBio ? user.bio.trim() : getString(R.string.profile_bio_empty));
        bioText.setTextColor(getColor(hasBio ? R.color.text_primary : R.color.text_secondary));

        showRank(points);
    }

    private void showRank(int points) {
        Rank rank = Rank.forPoints(points);
        Rank next = rank.next();

        rankBadge.setText(getString(R.string.rank_badge, getString(rank.label)));
        pointsText.setText(String.valueOf(points));
        rankProgress.setProgressCompat(Rank.progressPercent(points), true);

        if (next == null) {
            nextRankText.setText(R.string.profile_top_rank);
            pointsToGoText.setText(R.string.profile_top_rank_body);
        } else {
            nextRankText.setText(getString(R.string.profile_next_rank, getString(next.label)));
            int toGo = Rank.pointsToNextRank(points);
            pointsToGoText.setText(getResources().getQuantityString(R.plurals.profile_points_to_go, toGo, toGo));
        }
    }

    private void loadPreferences() {
        ApiClient.getService(this)
                .getPreferences()
                .enqueue(new Callback<PreferencesResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PreferencesResponse> call,
                                           @NonNull Response<PreferencesResponse> response) {
                        if (isDestroyed()) return;
                        // 204 has no body: the wizard was never completed.
                        if (response.isSuccessful()) {
                            showPreferences(response.body());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PreferencesResponse> call, @NonNull Throwable t) {
                        // The profile request already reports network problems.
                    }
                });
    }

    private void showPreferences(PreferencesResponse preferences) {
        boolean hasPreferences = preferences != null
                && preferences.travelModes != null && !preferences.travelModes.isEmpty();
        preferencesChips.setVisibility(hasPreferences ? View.VISIBLE : View.GONE);
        editPreferencesLink.setVisibility(hasPreferences ? View.VISIBLE : View.GONE);
        preferencesEmpty.setVisibility(hasPreferences ? View.GONE : View.VISIBLE);
        if (!hasPreferences) return;

        preferencesChips.removeAllViews();
        addTags(PreferencesWizard.Step.TRAVEL_MODE, preferences.travelModes);
        addTags(PreferencesWizard.Step.INTERESTS, preferences.interests);
        addTags(PreferencesWizard.Step.PRIORITY, preferences.priorities);
        addTags(PreferencesWizard.Step.PACE, preferences.paces);
    }

    /** Adds the chosen values in the wizard's order, so the tags don't shuffle between visits. */
    private void addTags(PreferencesWizard.Step step, List<String> chosen) {
        if (chosen == null) return;
        String[] labels = getResources().getStringArray(step.labels);
        for (int i = 0; i < step.values.size(); i++) {
            if (!chosen.contains(step.values.get(i))) continue;

            Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.item_tag_chip, preferencesChips, false);
            chip.setText(labels[i]);
            preferencesChips.addView(chip);
        }
    }

    /** Loads the next page of saved plans, or starts over from the first page. */
    private void loadAdventures(boolean reset) {
        if (loadingAdventures) return;
        loadingAdventures = true;
        if (reset) nextAdventuresPage = 0;
        showMoreAdventuresButton.setVisibility(View.GONE);

        ApiClient.getService(this)
                .getSavedTrips(nextAdventuresPage, PAGE_SIZE)
                .enqueue(new Callback<PageResponse<TripSummaryResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<PageResponse<TripSummaryResponse>> call,
                                           @NonNull Response<PageResponse<TripSummaryResponse>> response) {
                        if (isDestroyed()) return;
                        loadingAdventures = false;

                        if (response.isSuccessful() && response.body() != null) {
                            showAdventures(response.body(), reset);
                        } else if (response.code() != 401) {
                            Toast.makeText(ProfileActivity.this,
                                    R.string.profile_adventures_load_failed, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PageResponse<TripSummaryResponse>> call,
                                          @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        loadingAdventures = false;
                        // Lets the traveler retry the page that failed.
                        showMoreAdventuresButton.setVisibility(adventuresList.getChildCount() > 0
                                ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void showAdventures(PageResponse<TripSummaryResponse> page, boolean reset) {
        if (reset) adventuresList.removeAllViews();
        if (page.content != null) {
            for (TripSummaryResponse trip : page.content) {
                adventuresList.addView(adventureRow(trip));
            }
        }
        nextAdventuresPage = page.page + 1;
        adventuresEmpty.setVisibility(page.totalElements == 0 ? View.VISIBLE : View.GONE);
        showMoreAdventuresButton.setVisibility(page.hasNext() ? View.VISIBLE : View.GONE);
    }

    private View adventureRow(TripSummaryResponse trip) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_history_row, adventuresList, false);
        ((TextView) row.findViewById(R.id.rowIcon)).setText(Avatars.initialOf(trip.destination));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(trip.country == null ? trip.destination
                : getString(R.string.profile_adventure_place, trip.destination, trip.country));
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(Formats.dateRange(trip.startDate, trip.endDate));
        row.setOnClickListener(v -> openAdventure(trip.id));
        return row;
    }

    /** The list has no stops, so the whole plan loads when it is opened. */
    private void openAdventure(String tripId) {
        if (openingAdventure) return;
        openingAdventure = true;
        ApiClient.getService(this).getTrip(tripId).enqueue(new Callback<TripResponse>() {
            @Override
            public void onResponse(@NonNull Call<TripResponse> call, @NonNull Response<TripResponse> response) {
                openingAdventure = false;
                if (isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    Intent intent = new Intent(ProfileActivity.this, PlanActivity.class);
                    intent.putExtra("trip", new Gson().toJson(response.body()));
                    startActivity(intent);
                } else if (response.code() == 401) {
                    Session.expire(ProfileActivity.this);
                } else {
                    Toast.makeText(ProfileActivity.this,
                            R.string.profile_adventure_open_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<TripResponse> call, @NonNull Throwable t) {
                openingAdventure = false;
                if (!isDestroyed()) {
                    Toast.makeText(ProfileActivity.this, R.string.error_network, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /** Loads the next page of countries, or starts over from the first page. */
    private void loadCountries(boolean reset) {
        if (loadingCountries) return;
        loadingCountries = true;
        if (reset) nextCountriesPage = 0;

        historyProgress.setVisibility(View.VISIBLE);
        showMoreCountriesButton.setVisibility(View.GONE);

        ApiClient.getService(this)
                .getVisitedCountries(nextCountriesPage, PAGE_SIZE)
                .enqueue(new Callback<PageResponse<CountryHistoryResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<PageResponse<CountryHistoryResponse>> call,
                                           @NonNull Response<PageResponse<CountryHistoryResponse>> response) {
                        if (isDestroyed()) return;
                        loadingCountries = false;
                        historyProgress.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            showCountries(response.body(), reset);
                        } else if (response.code() != 401) {
                            Toast.makeText(ProfileActivity.this,
                                    R.string.history_load_failed, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PageResponse<CountryHistoryResponse>> call,
                                          @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        loadingCountries = false;
                        historyProgress.setVisibility(View.GONE);
                        // Lets the traveler retry the page that failed.
                        showMoreCountriesButton.setVisibility(countriesList.getChildCount() > 0
                                ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void showCountries(PageResponse<CountryHistoryResponse> page, boolean reset) {
        if (reset) countriesList.removeAllViews();

        List<CountryHistoryResponse> countries = page.content;
        if (countries != null) {
            for (CountryHistoryResponse country : countries) {
                countriesList.addView(countryRow(country));
            }
        }

        nextCountriesPage = page.page + 1;
        countriesCountText.setText(String.valueOf(page.totalElements));
        historyEmpty.setVisibility(page.totalElements == 0 ? View.VISIBLE : View.GONE);
        showMoreCountriesButton.setVisibility(page.hasNext() ? View.VISIBLE : View.GONE);
    }

    private View countryRow(CountryHistoryResponse country) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_history_row, countriesList, false);
        int places = (int) country.objectivesVisited;

        ((TextView) row.findViewById(R.id.rowIcon)).setText(Avatars.initialOf(country.country));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(country.country);
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(getString(R.string.country_meta,
                getResources().getQuantityString(R.plurals.places_count, places, places),
                Formats.date(country.lastVisitedAt)));

        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, CountryVisitsActivity.class);
            intent.putExtra(CountryVisitsActivity.EXTRA_COUNTRY, country.country);
            startActivity(intent);
        });
        return row;
    }

    /** The About section opens the editor with the cursor already in the bio. */
    private void openEditProfile(boolean focusBio) {
        Intent intent = new Intent(this, EditProfileActivity.class);
        intent.putExtra(EditProfileActivity.EXTRA_FOCUS_BIO, focusBio);
        if (user != null) {
            intent.putExtra(EditProfileActivity.EXTRA_NAME, user.name);
            intent.putExtra(EditProfileActivity.EXTRA_ADDRESS, user.address);
            intent.putExtra(EditProfileActivity.EXTRA_BIO, user.bio);
            intent.putExtra(EditProfileActivity.EXTRA_PICTURE, user.profilePicture);
        }
        startActivity(intent);
    }

    private void openPreferences() {
        startActivity(new Intent(this, PreferencesActivity.class));
    }

    private void logout() {
        new TokenManager(this).clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

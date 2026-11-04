package com.wanderly.ui.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.wanderly.R;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.Locale;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.GenerateTripRequest;
import com.wanderly.ui.adventure.PlanActivity;
import com.wanderly.ui.common.Session;

public class AdventureActivity extends AppCompatActivity {

    private Long startMillis, endMillis;
    private int startHour = 9, startMinute = 0, endHour = 18, endMinute = 0;

    /** An autocomplete suggestion: city name, country and centre, sent along so the backend skips a lookup. */
    private static final class City {
        final String name, country;
        final double latitude, longitude;
        City(String name, String country, double latitude, double longitude) {
            this.name = name; this.country = country; this.latitude = latitude; this.longitude = longitude;
        }
    }

    // Label shown in the dropdown ("Paris, France") -> the suggestion behind it.
    private final java.util.Map<String, City> suggestions = new java.util.HashMap<>();
    private GenerateTripViewModel viewModel;
    private TextInputLayout destinationLayout, datesLayout;
    private com.google.android.material.chip.ChipGroup paceGroup;
    private boolean paceChanged;  // a late answer from the server must not undo the traveler's choice

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adventure);
        if (savedInstanceState != null) {  // keep the chosen dates and hours across a rotation
            if (savedInstanceState.containsKey("startMillis")) {
                startMillis = savedInstanceState.getLong("startMillis");
                endMillis = savedInstanceState.getLong("endMillis");
            }
            startHour = savedInstanceState.getInt("startHour", startHour);
            endHour = savedInstanceState.getInt("endHour", endHour);
        }

        // The plan request lives in the ViewModel, so rotating the phone while it runs loses nothing.
        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(GenerateTripViewModel.class);
        viewModel.loading().observe(this, this::setLoading);
        viewModel.outcome().observe(this, outcome -> {
            if (outcome == null || !outcome.consume()) return;
            if (outcome.trip != null) {
                Intent i = new Intent(AdventureActivity.this, PlanActivity.class);
                i.putExtra("trip", new com.google.gson.Gson().toJson(outcome.trip));
                startActivity(i);
            } else if (outcome.code == 401) {
                Session.expire(AdventureActivity.this);
            } else if (outcome.code == 0 || outcome.code >= 500 || outcome.code == 429) {
                showRetry(outcome.request, outcome.message);
            } else {
                // A 4xx (bad dates, too long...) fails the same way again, so no Retry.
                showError(outcome.message);
            }
        });

        destinationLayout = findViewById(R.id.destinationLayout);
        datesLayout = findViewById(R.id.datesLayout);
        TextInputEditText datesInput = findViewById(R.id.datesInput);
        datesInput.setOnClickListener(v -> {
            com.google.android.material.datepicker.CalendarConstraints constraints =
                    new com.google.android.material.datepicker.CalendarConstraints.Builder()
                            .setValidator(com.google.android.material.datepicker.DateValidatorPointForward
                                    .from(MaterialDatePicker.todayInUtcMilliseconds()))
                            .build();

            MaterialDatePicker<Pair<Long, Long>> picker =
                    MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText("Trip dates")
                            .setCalendarConstraints(constraints)
                            .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                long days = (selection.second - selection.first) / 86_400_000L + 1;
                if (days > 14) {
                    datesLayout.setError(getString(R.string.error_trip_too_long));
                    return;
                }
                datesLayout.setErrorEnabled(false);
                startMillis = selection.first;
                endMillis = selection.second;
                datesInput.setText(picker.getHeaderText());
            });
            picker.show(getSupportFragmentManager(), "dates");
        });

        com.google.android.material.chip.ChipGroup rhythmGroup = findViewById(R.id.rhythmGroup);
        rhythmGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipEarly) { startHour = 7; endHour = 16; }
            else if (id == R.id.chipNight) { startHour = 12; endHour = 23; }
            else { startHour = 9; endHour = 18; }
            startMinute = 0;
            endMinute = 0;
        });

        paceGroup = findViewById(R.id.paceGroup);
        paceGroup.setOnCheckedStateChangeListener((group, ids) -> paceChanged = true);
        if (savedInstanceState == null) preselectPace();

        MaterialAutoCompleteTextView destinationInput = findViewById(R.id.destinationInput);
        setupAutocomplete(destinationInput);
        Button generateButton = findViewById(R.id.generateButton);

        generateButton.setOnClickListener(v -> {
            String destination = destinationInput.getText() == null
                    ? "" : destinationInput.getText().toString().trim();

            if (destination.isEmpty()) {
                destinationLayout.setError(getString(R.string.error_destination_required));
                return;
            }
            if (startMillis == null || endMillis == null) {
                datesLayout.setError(getString(R.string.error_dates_required));
                return;
            }

            GenerateTripRequest req = new GenerateTripRequest();
            req.destination = destination;
            City city = suggestions.get(destination);
            if (city != null) {
                req.destination = city.name;
                req.country = city.country;
                req.latitude = city.latitude;
                req.longitude = city.longitude;
            }
            req.startDate = java.time.Instant.ofEpochMilli(startMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            req.endDate = java.time.Instant.ofEpochMilli(endMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            req.dayStart = String.format(java.util.Locale.US, "%02d:%02d", startHour, startMinute);
            req.dayEnd = String.format(java.util.Locale.US, "%02d:%02d", endHour, endMinute);
            req.pace = selectedPace();

            sendRequest(req);
        });
    }

    /** Starts on the saved pace; with several saved, on the fullest one, as the backend does. */
    private void preselectPace() {
        ApiClient.getService(getApplicationContext()).getPreferences().enqueue(
                new retrofit2.Callback<com.wanderly.data.remote.dto.PreferencesResponse>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<com.wanderly.data.remote.dto.PreferencesResponse> call,
                                           @NonNull retrofit2.Response<com.wanderly.data.remote.dto.PreferencesResponse> response) {
                        com.wanderly.data.remote.dto.PreferencesResponse body = response.body();
                        if (isDestroyed() || paceChanged || body == null || body.paces == null) return;
                        if (body.paces.contains("PACKED")) paceGroup.check(R.id.chipPacked);
                        else if (body.paces.contains("BALANCED")) paceGroup.check(R.id.chipBalanced);
                        else if (body.paces.contains("RELAXED")) paceGroup.check(R.id.chipRelaxed);
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<com.wanderly.data.remote.dto.PreferencesResponse> call,
                                          @NonNull Throwable t) {
                        // Balanced stays selected; the plan request reports network problems.
                    }
                });
    }

    private String selectedPace() {
        int id = paceGroup.getCheckedChipId();
        if (id == R.id.chipRelaxed) return "RELAXED";
        if (id == R.id.chipPacked) return "PACKED";
        return "BALANCED";
    }

    private void setLoading(boolean loading) {
        findViewById(R.id.generateProgress)
                .setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        findViewById(R.id.generateButton).setEnabled(!loading);
    }

    private void sendRequest(GenerateTripRequest req) {
        viewModel.generate(ApiClient.getService(getApplicationContext()), req);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (startMillis != null && endMillis != null) {
            outState.putLong("startMillis", startMillis);
            outState.putLong("endMillis", endMillis);
        }
        outState.putInt("startHour", startHour);
        outState.putInt("endHour", endHour);
    }

    private void showRetry(GenerateTripRequest req, String message) {
        com.google.android.material.snackbar.Snackbar
                .make(findViewById(R.id.generateButton), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                .setAction("Retry", v -> sendRequest(req))
                .setTextMaxLines(4)
                .show();
    }

    private void showError(String message) {
        com.google.android.material.snackbar.Snackbar
                .make(findViewById(R.id.generateButton), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setTextMaxLines(4)
                .show();
    }

    // --- B2: destination autocomplete (Open-Meteo geocoding, free, no key) ---
    private final android.os.Handler debounce = new android.os.Handler(android.os.Looper.getMainLooper());
    private final okhttp3.OkHttpClient geoClient = new okhttp3.OkHttpClient();
    private Runnable pendingSearch;
    private boolean suppressSearch;

    private void setupAutocomplete(MaterialAutoCompleteTextView input) {
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, new java.util.ArrayList<>());
        input.setAdapter(adapter);
        input.setOnItemClickListener((parent, view, pos, id) -> suppressSearch = true);

        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                destinationLayout.setErrorEnabled(false);
                if (suppressSearch) { suppressSearch = false; return; }
                if (pendingSearch != null) debounce.removeCallbacks(pendingSearch);
                String q = s.toString().trim();
                if (q.length() < 2) return;
                pendingSearch = () -> searchCities(q, input, adapter);
                debounce.postDelayed(pendingSearch, 300);
            }
        });
    }

    private void searchCities(String q, MaterialAutoCompleteTextView input,
                              android.widget.ArrayAdapter<String> adapter) {
        okhttp3.HttpUrl url = okhttp3.HttpUrl.parse("https://geocoding-api.open-meteo.com/v1/search")
                .newBuilder()
                .addQueryParameter("name", q)
                .addQueryParameter("count", "8")
                .addQueryParameter("language", "en")
                .build();
        geoClient.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {}

            @Override public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response resp)
                    throws java.io.IOException {
                if (!resp.isSuccessful() || resp.body() == null) return;
                com.google.gson.JsonObject root = com.google.gson.JsonParser
                        .parseString(resp.body().string()).getAsJsonObject();
                java.util.LinkedHashMap<String, City> found = new java.util.LinkedHashMap<>();
                if (root.has("results")) {
                    for (com.google.gson.JsonElement el : root.getAsJsonArray("results")) {
                        com.google.gson.JsonObject o = el.getAsJsonObject();
                        String name = o.get("name").getAsString();
                        String country = o.has("country") ? o.get("country").getAsString() : null;
                        String label = country == null ? name : name + ", " + country;
                        if (o.has("latitude") && o.has("longitude") && !found.containsKey(label)) {
                            found.put(label, new City(name, country,
                                    o.get("latitude").getAsDouble(), o.get("longitude").getAsDouble()));
                        }
                    }
                }
                java.util.Set<String> names = found.keySet();
                runOnUiThread(() -> {
                    if (isDestroyed() || !q.equals(input.getText().toString().trim())) return;
                    suggestions.putAll(found);
                    adapter.clear();
                    adapter.addAll(names);
                    adapter.notifyDataSetChanged();
                    if (!names.isEmpty() && input.hasFocus()) input.showDropDown();
                });
            }
        });
    }

}
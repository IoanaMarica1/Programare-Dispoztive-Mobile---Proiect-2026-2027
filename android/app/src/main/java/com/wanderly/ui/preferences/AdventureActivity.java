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
import android.widget.Toast;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.GenerateTripRequest;
import com.wanderly.data.remote.dto.TripResponse;
import com.wanderly.ui.adventure.PlanActivity;
import com.wanderly.ui.common.Session;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdventureActivity extends AppCompatActivity {

    private Long startMillis, endMillis;
    private int startHour = 9, startMinute = 0, endHour = 18, endMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adventure);

        Button datesButton = findViewById(R.id.datesButton);
        datesButton.setOnClickListener(v -> {
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
                    Toast.makeText(this, "Trips can last at most 14 days", Toast.LENGTH_LONG).show();
                    return;
                }
                startMillis = selection.first;
                endMillis = selection.second;
                datesButton.setText(picker.getHeaderText());
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

        MaterialAutoCompleteTextView destinationInput = findViewById(R.id.destinationInput);
        setupAutocomplete(destinationInput);
        Button generateButton = findViewById(R.id.generateButton);

        generateButton.setOnClickListener(v -> {
            String destination = destinationInput.getText() == null
                    ? "" : destinationInput.getText().toString().trim();

            if (destination.isEmpty()) {
                destinationInput.setError("Enter a destination");
                return;
            }
            if (startMillis == null || endMillis == null) {
                Toast.makeText(this, "Pick your trip dates", Toast.LENGTH_SHORT).show();
                return;
            }

            GenerateTripRequest req = new GenerateTripRequest();
            req.destination = destination;
            req.startDate = java.time.Instant.ofEpochMilli(startMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            req.endDate = java.time.Instant.ofEpochMilli(endMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            req.dayStart = String.format(java.util.Locale.US, "%02d:%02d", startHour, startMinute);
            req.dayEnd = String.format(java.util.Locale.US, "%02d:%02d", endHour, endMinute);

            sendRequest(req);
        });
    }

    private void setLoading(boolean loading) {
        findViewById(R.id.generateProgress)
                .setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        findViewById(R.id.generateButton).setEnabled(!loading);
    }

    private void sendRequest(GenerateTripRequest req) {
        setLoading(true);
        ApiClient.getService(this).generateTrip(req).enqueue(new Callback<TripResponse>() {
            @Override
            public void onResponse(@NonNull Call<TripResponse> call,
                                   @NonNull Response<TripResponse> response) {
                if (isDestroyed()) return;
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Intent i = new Intent(AdventureActivity.this, PlanActivity.class);
                    i.putExtra("trip", new com.google.gson.Gson().toJson(response.body()));
                    startActivity(i);
                } else if (response.code() == 401) {
                    Session.expire(AdventureActivity.this);
                } else {
                    showRetry(req, "Could not generate plan (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<TripResponse> call, @NonNull Throwable t) {
                if (isDestroyed()) return;
                setLoading(false);
                showRetry(req, "Network error");
            }
        });
    }

    private void showRetry(GenerateTripRequest req, String message) {
        com.google.android.material.snackbar.Snackbar
                .make(findViewById(R.id.generateButton), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                .setAction("Retry", v -> sendRequest(req))
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
                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                if (root.has("results")) {
                    for (com.google.gson.JsonElement el : root.getAsJsonArray("results")) {
                        com.google.gson.JsonObject o = el.getAsJsonObject();
                        String name = o.get("name").getAsString();
                        String country = o.has("country") ? o.get("country").getAsString() : null;
                        names.add(country == null ? name : name + ", " + country);
                    }
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || !q.equals(input.getText().toString().trim())) return;
                    adapter.clear();
                    adapter.addAll(names);
                    adapter.notifyDataSetChanged();
                    if (!names.isEmpty() && input.hasFocus()) input.showDropDown();
                });
            }
        });
    }

}
package com.wanderly.ui.preferences;

import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.wanderly.R;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.Locale;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.GenerateTripRequest;
import com.wanderly.data.remote.dto.TripResponse;
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
            MaterialDatePicker<Pair<Long, Long>> picker =
                    MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText("Trip dates")
                            .build();
            picker.addOnPositiveButtonClickListener(selection -> {
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

        TextInputEditText destinationInput = findViewById(R.id.destinationInput);
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

            // Placeholder until the backend endpoint exists (B3).
            GenerateTripRequest req = new GenerateTripRequest();
            req.destination = destination;
            req.startDate = java.time.Instant.ofEpochMilli(startMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            req.endDate = java.time.Instant.ofEpochMilli(endMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            req.dayStart = String.format(java.util.Locale.US, "%02d:%02d", startHour, startMinute);
            req.dayEnd = String.format(java.util.Locale.US, "%02d:%02d", endHour, endMinute);

            generateButton.setEnabled(false);
            ApiClient.getService(this).generateTrip(req).enqueue(new Callback<TripResponse>() {
                @Override
                public void onResponse(@NonNull Call<TripResponse> call,
                                       @NonNull Response<TripResponse> response) {
                    if (isDestroyed()) return;
                    generateButton.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(AdventureActivity.this,
                                "Plan ready: " + response.body().stops.size() + " stops",
                                Toast.LENGTH_LONG).show();
                    } else if (response.code() == 401) {
                        Session.expire(AdventureActivity.this);
                    } else {
                        Toast.makeText(AdventureActivity.this,
                                "Could not generate plan (" + response.code() + ")",
                                Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<TripResponse> call, @NonNull Throwable t) {
                    if (isDestroyed()) return;
                    generateButton.setEnabled(true);
                    Toast.makeText(AdventureActivity.this,
                            "Network error", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
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
            Toast.makeText(this, "Ready: " + destination, Toast.LENGTH_LONG).show();
        });
    }
}
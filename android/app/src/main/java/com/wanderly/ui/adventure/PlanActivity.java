package com.wanderly.ui.adventure;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.wanderly.R;
import com.wanderly.data.remote.dto.TripResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class PlanActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan);

        String json = getIntent().getStringExtra("trip");
        TripResponse trip = json == null ? null : new Gson().fromJson(json, TripResponse.class);
        if (trip == null || trip.stops == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.planTitle)).setText(trip.destination);
        ((TextView) findViewById(R.id.planSubtitle))
                .setText(trip.startDate + "  →  " + trip.endDate);

        LinearLayout container = findViewById(R.id.planContainer);

        // Group stops by day, ordered by position inside each day.
        TreeMap<Integer, List<TripResponse.Stop>> byDay = new TreeMap<>();
        for (TripResponse.Stop s : trip.stops) {
            byDay.computeIfAbsent(s.dayNumber, k -> new ArrayList<>()).add(s);
        }

        for (java.util.Map.Entry<Integer, List<TripResponse.Stop>> e : byDay.entrySet()) {
            List<TripResponse.Stop> stops = e.getValue();
            stops.sort((a, b) -> Integer.compare(a.position, b.position));

            container.addView(text("Day " + e.getKey(), 20, 0xFFFFFFFF, true, 28));

            for (TripResponse.Stop s : stops) {
                String time = s.startTime != null && s.startTime.length() >= 5
                        ? s.startTime.substring(0, 5) : "";
                container.addView(text(s.position + ".  " + s.name, 17, 0xFFFFFFFF, true, 14));
                String meta = time + "  ·  " + s.durationMinutes + " min"
                        + (s.category != null ? "  ·  " + s.category : "");
                container.addView(text(meta, 14, 0xFFB0BEC5, false, 2));
            }
        }
    }

    private TextView text(String value, int sp, int color, boolean bold, int topDp) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.START);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (topDp * getResources().getDisplayMetrics().density);
        tv.setLayoutParams(lp);
        return tv;
    }
}
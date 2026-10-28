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

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class PlanActivity extends AppCompatActivity {

    private MapView mapView;
    private org.maplibre.android.maps.MapLibreMap mapRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_plan);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        String json = getIntent().getStringExtra("trip");
        TripResponse trip = json == null ? null : new Gson().fromJson(json, TripResponse.class);
        if (trip == null || trip.stops == null) {
            finish();
            return;
        }

        mapView.getMapAsync(map -> map.setStyle(
                new org.maplibre.android.maps.Style.Builder()
                        .fromUri("https://tiles.openfreemap.org/styles/liberty"),
                style -> {
                    mapRef = map;
                    List<TripResponse.Stop> sorted = new ArrayList<>(trip.stops);
                    sorted.sort((a, b) -> a.dayNumber != b.dayNumber
                            ? Integer.compare(a.dayNumber, b.dayNumber)
                            : Integer.compare(a.position, b.position));

                    StringBuilder pts = new StringBuilder(
                            "{\"type\":\"FeatureCollection\",\"features\":[");
                    StringBuilder coords = new StringBuilder();
                    org.maplibre.android.geometry.LatLngBounds.Builder bounds =
                            new org.maplibre.android.geometry.LatLngBounds.Builder();

                    for (int i = 0; i < sorted.size(); i++) {
                        TripResponse.Stop s = sorted.get(i);
                        if (i > 0) { pts.append(","); coords.append(","); }
                        pts.append("{\"type\":\"Feature\",\"properties\":{\"num\":\"")
                                .append(s.position)
                                .append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                                .append(s.longitude).append(",").append(s.latitude).append("]}}");
                        coords.append("[").append(s.longitude).append(",").append(s.latitude).append("]");
                        bounds.include(new LatLng(s.latitude, s.longitude));
                    }
                    pts.append("]}");
                    String lineJson = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":"
                            + "{\"type\":\"LineString\",\"coordinates\":[" + coords + "]}}";

                    style.addSource(new org.maplibre.android.style.sources.GeoJsonSource("route", lineJson));
                    style.addSource(new org.maplibre.android.style.sources.GeoJsonSource("stops", pts.toString()));

                    style.addLayer(new org.maplibre.android.style.layers.LineLayer("route-layer", "route")
                            .withProperties(
                                    org.maplibre.android.style.layers.PropertyFactory.lineColor("#0284C7"),
                                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(4f)));
                    style.addLayer(new org.maplibre.android.style.layers.CircleLayer("stops-circle", "stops")
                            .withProperties(
                                    org.maplibre.android.style.layers.PropertyFactory.circleRadius(14f),
                                    org.maplibre.android.style.layers.PropertyFactory.circleColor("#0F4C81"),
                                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(2f)));
                    style.addLayer(new org.maplibre.android.style.layers.SymbolLayer("stops-num", "stops")
                            .withProperties(
                                    org.maplibre.android.style.layers.PropertyFactory.textField(
                                            org.maplibre.android.style.expressions.Expression.get("num")),
                                    org.maplibre.android.style.layers.PropertyFactory.textColor("#FFFFFF"),
                                    org.maplibre.android.style.layers.PropertyFactory.textSize(14f),
                                    org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap(true),
                                    org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement(true),
                                    org.maplibre.android.style.layers.PropertyFactory.textFont(
                                            new String[]{"Noto Sans Regular"})));

                    loadWalkingRoute(sorted, style);
                    if (!sorted.isEmpty()) {
                        map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory
                                .newLatLngBounds(bounds.build(), 100));
                    }
                }));

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
                TextView nameView = text(s.position + ".  " + s.name, 17, 0xFFFFFFFF, true, 14);
                nameView.setOnClickListener(v -> {
                    android.widget.Toast.makeText(this, "tap " + s.name + ", map ready: " + (mapRef != null),
                            android.widget.Toast.LENGTH_SHORT).show();
                    if (mapRef != null) {
                        mapRef.animateCamera(org.maplibre.android.camera.CameraUpdateFactory
                                .newLatLngZoom(new LatLng(s.latitude, s.longitude), 16));
                    }
                });
                container.addView(nameView);
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

    /** Asks the free OSRM walking server for the street route through all stops. */
    private void loadWalkingRoute(List<TripResponse.Stop> stops,
                                  org.maplibre.android.maps.Style style) {
        if (stops.size() < 2) return;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < stops.size(); i++) {
            if (i > 0) path.append(";");
            path.append(stops.get(i).longitude).append(",").append(stops.get(i).latitude);
        }
        String url = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/"
                + path + "?overview=full&geometries=geojson";

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", "WanderlyStudentApp/0.1");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                org.json.JSONObject geometry = new org.json.JSONObject(sb.toString())
                        .getJSONArray("routes").getJSONObject(0).getJSONObject("geometry");
                String feature = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":"
                        + geometry + "}";
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    org.maplibre.android.style.sources.GeoJsonSource src =
                            style.getSourceAs("route");
                    if (src != null) src.setGeoJson(feature);
                });
            } catch (Exception ignored) {
                // Keep the straight line if routing is unavailable.
            }
        });
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onDestroy() { mapView.onDestroy(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
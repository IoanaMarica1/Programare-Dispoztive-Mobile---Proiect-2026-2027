package com.wanderly.ui.adventure;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.wanderly.R;
import com.wanderly.data.remote.dto.TripResponse;

import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class PlanActivity extends AppCompatActivity {

    private static final String EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}";

    private MapView mapView;
    private MapLibreMap mapRef;
    private Style styleRef;
    private TripResponse trip;
    private LinearLayout container;
    private int fixedChildren;
    private int selectedDay = 1;
    private final TreeMap<Integer, List<TripResponse.Stop>> byDay = new TreeMap<>();
    private final Map<String, TextView> nameViews = new HashMap<>();

    private final List<TextView> legViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_plan);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        String json = getIntent().getStringExtra("trip");
        trip = json == null ? null : new Gson().fromJson(json, TripResponse.class);
        if (trip == null || trip.stops == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.planTitle)).setText(trip.destination);
        ((TextView) findViewById(R.id.planSubtitle))
                .setText(trip.startDate + "  →  " + trip.endDate);
        container = findViewById(R.id.planContainer);

        for (TripResponse.Stop s : trip.stops) {
            byDay.computeIfAbsent(s.dayNumber, k -> new ArrayList<>()).add(s);
        }
        for (List<TripResponse.Stop> l : byDay.values()) {
            l.sort((a, b) -> Integer.compare(a.position, b.position));
        }

        // Day chips
        ChipGroup group = new ChipGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        List<Chip> chips = new ArrayList<>();
        for (Integer day : byDay.keySet()) {
            Chip chip = new Chip(this);
            chip.setText("Day " + day);
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) showDay(day);
            });
            group.addView(chip);
            chips.add(chip);
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(group);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        container.addView(chipScroll, lp);
        fixedChildren = container.getChildCount();

        if (!chips.isEmpty()) chips.get(0).setChecked(true);

        mapView.getMapAsync(map -> map.setStyle(
                new Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"),
                style -> {
                    mapRef = map;
                    styleRef = style;
                    style.addSource(new GeoJsonSource("route", EMPTY));
                    style.addSource(new GeoJsonSource("stops", EMPTY));
                    style.addLayer(new LineLayer("route-layer", "route").withProperties(
                            PropertyFactory.lineColor("#0284C7"),
                            PropertyFactory.lineWidth(4f)));
                    style.addLayer(new CircleLayer("stops-circle", "stops").withProperties(
                            PropertyFactory.circleRadius(14f),
                            PropertyFactory.circleColor("#0F4C81"),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(2f)));
                    style.addLayer(new SymbolLayer("stops-num", "stops").withProperties(
                            PropertyFactory.textField(Expression.get("num")),
                            PropertyFactory.textColor("#FFFFFF"),
                            PropertyFactory.textSize(14f),
                            PropertyFactory.textAllowOverlap(true),
                            PropertyFactory.textIgnorePlacement(true),
                            PropertyFactory.textFont(new String[]{"Noto Sans Regular"})));

                    map.addOnMapClickListener(point -> {
                        android.graphics.PointF px = map.getProjection().toScreenLocation(point);
                        List<Feature> hits = map.queryRenderedFeatures(px, "stops-circle");
                        if (hits.isEmpty()) return false;
                        TextView target = nameViews.get(hits.get(0).getStringProperty("key"));
                        if (target != null) {
                            ScrollView scroll = (ScrollView) container.getParent();
                            scroll.smoothScrollTo(0, Math.max(0, target.getTop() - 40));
                        }
                        return true;
                    });

                    updateMap(selectedDay);
                }));
    }

    /** Rebuilds the list for one day and refreshes the map. */
    private void showDay(int day) {
        selectedDay = day;
        while (container.getChildCount() > fixedChildren) {
            container.removeViewAt(fixedChildren);
        }
        nameViews.clear();
        legViews.clear();

        List<TripResponse.Stop> stops = byDay.get(day);
        if (stops == null) return;
        container.addView(text("Day " + day, 20, 0xFFFFFFFF, true, 20));

        for (int idx = 0; idx < stops.size(); idx++) {
            TripResponse.Stop s = stops.get(idx);
            if (idx > 0) {
                TextView leg = text("", 13, 0xFF86EFAC, false, 10);
                leg.setVisibility(View.GONE);
                container.addView(leg);
                legViews.add(leg);
            }
            String time = s.startTime != null && s.startTime.length() >= 5
                    ? s.startTime.substring(0, 5) : "";
            TextView nameView = text(s.position + ".  " + s.name, 17, 0xFFFFFFFF, true, 14);
            nameView.setOnClickListener(v -> {
                if (mapRef != null) {
                    mapRef.animateCamera(CameraUpdateFactory
                            .newLatLngZoom(new LatLng(s.latitude, s.longitude), 16));
                }
            });
            container.addView(nameView);
            nameViews.put(s.dayNumber + "-" + s.position, nameView);
            String meta = time + "  ·  " + s.durationMinutes + " min"
                    + (s.category != null ? "  ·  " + s.category : "");
            container.addView(text(meta, 14, 0xFFB0BEC5, false, 2));
        }
        if (styleRef != null) updateMap(day);
    }

    /** Shows only the selected day's markers and route on the map. */
    private void updateMap(int day) {
        List<TripResponse.Stop> stops = byDay.get(day);
        if (stops == null || styleRef == null || mapRef == null) return;

        StringBuilder pts = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        StringBuilder coords = new StringBuilder();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (int i = 0; i < stops.size(); i++) {
            TripResponse.Stop s = stops.get(i);
            if (i > 0) { pts.append(","); coords.append(","); }
            pts.append("{\"type\":\"Feature\",\"properties\":{\"num\":\"").append(s.position)
                    .append("\",\"key\":\"").append(s.dayNumber).append("-").append(s.position)
                    .append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                    .append(s.longitude).append(",").append(s.latitude).append("]}}");
            coords.append("[").append(s.longitude).append(",").append(s.latitude).append("]");
            bounds.include(new LatLng(s.latitude, s.longitude));
        }
        pts.append("]}");

        String line = stops.size() < 2 ? EMPTY
                : "{\"type\":\"Feature\",\"properties\":{},\"geometry\":"
                  + "{\"type\":\"LineString\",\"coordinates\":[" + coords + "]}}";

        GeoJsonSource stopsSrc = styleRef.getSourceAs("stops");
        GeoJsonSource routeSrc = styleRef.getSourceAs("route");
        if (stopsSrc != null) stopsSrc.setGeoJson(pts.toString());
        if (routeSrc != null) routeSrc.setGeoJson(line);

        if (stops.size() >= 2) {
            mapRef.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));
        } else if (stops.size() == 1) {
            mapRef.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(stops.get(0).latitude, stops.get(0).longitude), 14));
        }
        loadWalkingRoute(stops, day);
    }

    /** Asks the free OSRM walking server for the street route through the day's stops. */
    private void loadWalkingRoute(List<TripResponse.Stop> stops, int day) {
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
                JSONObject route = new JSONObject(sb.toString())
                        .getJSONArray("routes").getJSONObject(0);
                String feature = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":"
                        + route.getJSONObject("geometry") + "}";
                JSONArray legs = route.getJSONArray("legs");
                List<String> legTexts = new ArrayList<>();
                for (int i = 0; i < legs.length(); i++) {
                    double meters = legs.getJSONObject(i).getDouble("distance");
                    long minutes = Math.max(1, Math.round(
                            legs.getJSONObject(i).getDouble("duration") / 60.0));
                    String dist = meters < 1000
                            ? Math.round(meters) + " m"
                            : String.format(java.util.Locale.US, "%.1f km", meters / 1000);
                    legTexts.add("🚶 " + minutes + " min walk  ·  " + dist);
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || day != selectedDay || styleRef == null) return;
                    GeoJsonSource src = styleRef.getSourceAs("route");
                    if (src != null) src.setGeoJson(feature);
                    for (int i = 0; i < legTexts.size() && i < legViews.size(); i++) {
                        legViews.get(i).setText(legTexts.get(i));
                        legViews.get(i).setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
                // Keep the straight line if routing is unavailable.
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
        lp.topMargin = dp(topDp);
        tv.setLayoutParams(lp);
        return tv;
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onDestroy() { mapView.onDestroy(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
package com.wanderly.ui.adventure;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.ApiService;
import com.wanderly.data.remote.dto.CandidateResponse;
import com.wanderly.data.remote.dto.StopRequest;
import com.wanderly.data.remote.dto.TripResponse;
import com.wanderly.ui.common.Session;

import org.json.JSONArray;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * B4: the itinerary (top) and the map (bottom), split by a draggable handle, with a full-screen map
 * toggle. B3: each stop can be swapped or removed and a day can get a new stop; the backend re-plans
 * that day (order and times) and returns the whole trip.
 */
public class PlanActivity extends AppCompatActivity {

    private static final String EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}";
    private static final float MIN_LIST = 0.15f, MAX_LIST = 0.85f;
    private static final int SELECTED_BG = 0xFF1E3A5F;

    private MapView mapView;
    private MapLibreMap mapRef;
    private Style styleRef;
    private TripResponse trip;
    private LinearLayout root, container;
    private ScrollView scroll;
    private View handle, mapFrame;
    private MaterialButton fullScreenButton;
    private int fixedChildren;
    private int dayCount;
    private int selectedDay = 1;
    private String selectedStopId;
    private float listFraction = 0.5f;
    private boolean fullScreenMap;
    private boolean busy;      // one edit at a time
    private int routeRequest;  // answers for an older day or plan are ignored

    private final TreeMap<Integer, List<TripResponse.Stop>> byDay = new TreeMap<>();
    private final Map<String, View> rowViews = new HashMap<>();
    private final List<TextView> legViews = new ArrayList<>();
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private final OnBackPressedCallback leaveFullScreen = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            setFullScreen(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_plan);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        // After a rotation the trip may differ from the intent's: the traveler may have edited it.
        String json = savedInstanceState != null ? savedInstanceState.getString("trip")
                : getIntent().getStringExtra("trip");
        trip = json == null ? null : new Gson().fromJson(json, TripResponse.class);
        if (trip == null || trip.stops == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            selectedDay = savedInstanceState.getInt("day", 1);
            selectedStopId = savedInstanceState.getString("stop");
            listFraction = savedInstanceState.getFloat("split", 0.5f);
            fullScreenMap = savedInstanceState.getBoolean("fullScreen", false);
        }

        root = findViewById(R.id.planRoot);
        scroll = findViewById(R.id.planScroll);
        handle = findViewById(R.id.dividerHandle);
        mapFrame = findViewById(R.id.mapFrame);
        fullScreenButton = findViewById(R.id.fullScreenButton);
        container = findViewById(R.id.planContainer);
        ((TextView) findViewById(R.id.planTitle)).setText(trip.destination);
        ((TextView) findViewById(R.id.planSubtitle)).setText(trip.startDate + "  →  " + trip.endDate);

        groupStops();
        dayCount = countDays();
        selectedDay = Math.min(Math.max(selectedDay, 1), dayCount);
        addDayChips();
        setupDivider();
        fullScreenButton.setOnClickListener(v -> setFullScreen(!fullScreenMap));
        getOnBackPressedDispatcher().addCallback(this, leaveFullScreen);
        setSplit(listFraction);
        setFullScreen(fullScreenMap);

        mapView.addOnDidFailLoadingMapListener(message ->
                findViewById(R.id.mapError).setVisibility(View.VISIBLE));
        mapView.getMapAsync(map -> map.setStyle(
                new Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"),
                style -> {
                    mapRef = map;
                    styleRef = style;
                    style.addSource(new GeoJsonSource("route", EMPTY));
                    style.addSource(new GeoJsonSource("stops", EMPTY));
                    style.addSource(new GeoJsonSource("selected", EMPTY));
                    style.addLayer(new LineLayer("route-layer", "route").withProperties(
                            PropertyFactory.lineColor("#0284C7"),
                            PropertyFactory.lineWidth(4f)));
                    style.addLayer(new CircleLayer("stops-circle", "stops").withProperties(
                            PropertyFactory.circleRadius(14f),
                            PropertyFactory.circleColor("#0F4C81"),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(2f)));
                    // The selected stop: a bigger orange marker under its number.
                    style.addLayer(new CircleLayer("selected-circle", "selected").withProperties(
                            PropertyFactory.circleRadius(19f),
                            PropertyFactory.circleColor("#F97316"),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(3f)));
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
                        TripResponse.Stop stop = findStop(hits.get(0).getStringProperty("key"));
                        if (stop != null) {
                            selectStop(stop, false);
                            View row = rowViews.get(stop.id);
                            if (row != null && !fullScreenMap) {
                                scroll.smoothScrollTo(0, Math.max(0, row.getTop() - 40));
                            }
                        }
                        return true;
                    });

                    updateMap(selectedDay);
                }));
    }

    // ------------------------------------------------------------------ days and stops

    private void groupStops() {
        byDay.clear();
        for (TripResponse.Stop s : trip.stops) {
            byDay.computeIfAbsent(s.dayNumber, k -> new ArrayList<>()).add(s);
        }
        for (List<TripResponse.Stop> l : byDay.values()) {
            l.sort((a, b) -> Integer.compare(a.position, b.position));
        }
    }

    /** Every day of the trip, also the ones without stops (they get an "add a stop" button). */
    private int countDays() {
        int fromStops = byDay.isEmpty() ? 1 : byDay.lastKey();
        try {
            int fromDates = (int) ChronoUnit.DAYS.between(
                    LocalDate.parse(trip.startDate), LocalDate.parse(trip.endDate)) + 1;
            return Math.max(fromDates, fromStops);
        } catch (RuntimeException e) {
            return fromStops;
        }
    }

    private void addDayChips() {
        ChipGroup group = new ChipGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        Chip toCheck = null;
        for (int day = 1; day <= dayCount; day++) {
            final int d = day;
            Chip chip = new Chip(this);
            chip.setText("Day " + day);
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) showDay(d);
            });
            group.addView(chip);
            if (day == selectedDay) toCheck = chip;
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(group);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        container.addView(chipScroll, lp);
        fixedChildren = container.getChildCount();
        if (toCheck != null) toCheck.setChecked(true);
    }

    /** Rebuilds the list for one day and refreshes the map. */
    private void showDay(int day) {
        selectedDay = day;
        while (container.getChildCount() > fixedChildren) {
            container.removeViewAt(fixedChildren);
        }
        rowViews.clear();
        legViews.clear();
        container.addView(text("Day " + day, 20, 0xFFFFFFFF, true, 20));

        List<TripResponse.Stop> stops = byDay.getOrDefault(day, new ArrayList<>());
        if (stops.isEmpty()) {
            container.addView(text("No stops planned for this day yet.", 14, 0xFFB0BEC5, false, 8));
        }
        for (int idx = 0; idx < stops.size(); idx++) {
            TripResponse.Stop s = stops.get(idx);
            if (idx > 0) {
                TextView leg = text("", 13, 0xFF86EFAC, false, 10);
                leg.setVisibility(View.GONE);
                container.addView(leg);
                legViews.add(leg);
            }
            container.addView(stopRow(s));
        }
        MaterialButton add = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        add.setText("+ Add a stop");
        add.setMinHeight(dp(44));
        add.setOnClickListener(v -> addStop(day));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        container.addView(add, lp);

        TripResponse.Stop selected = findStop(selectedStopId);
        if (selected == null || selected.dayNumber != day) selectedStopId = null;
        highlightRows();
        if (styleRef != null) updateMap(day);
    }

    private View stopRow(TripResponse.Stop s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> selectStop(s, true));

        String time = s.startTime != null && s.startTime.length() >= 5 ? s.startTime.substring(0, 5) : "";
        row.addView(text(s.position + ".  " + s.name, 17, 0xFFFFFFFF, true, 0));
        row.addView(text(time + "  ·  " + s.durationMinutes + " min"
                + (s.category != null ? "  ·  " + s.category : ""), 14, 0xFFB0BEC5, false, 2));
        if (s.description != null && !s.description.isEmpty()) {
            row.addView(text(s.description, 14, 0xFFE2E8F0, false, 4));
        }
        if (s.openingHours != null && !s.openingHours.isEmpty()) {
            row.addView(text("Open: " + s.openingHours, 13, 0xFF7DD3FC, false, 2));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(action("Swap", v -> swapStop(s)));
        actions.addView(action("Remove", v -> removeStop(s)));
        row.addView(actions);

        rowViews.put(s.id, row);
        return row;
    }

    private View action(String label, View.OnClickListener click) {
        MaterialButton b = new MaterialButton(this, null,
                androidx.appcompat.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setMinHeight(dp(44));
        b.setOnClickListener(click);
        return b;
    }

    private TripResponse.Stop findStop(String id) {
        if (id == null) return null;
        for (TripResponse.Stop s : trip.stops) {
            if (id.equals(s.id)) return s;
        }
        return null;
    }

    /** Highlights the stop in the list and on the map; centres the map on it when asked. */
    private void selectStop(TripResponse.Stop s, boolean centre) {
        selectedStopId = s.id;
        highlightRows();
        updateSelectedMarker();
        if (centre && mapRef != null) {
            mapRef.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(s.latitude, s.longitude), 16));
        }
    }

    private void highlightRows() {
        for (Map.Entry<String, View> e : rowViews.entrySet()) {
            e.getValue().setBackgroundColor(e.getKey().equals(selectedStopId) ? SELECTED_BG : 0);
        }
    }

    private void updateSelectedMarker() {
        if (styleRef == null) return;
        GeoJsonSource src = styleRef.getSourceAs("selected");
        if (src == null) return;
        TripResponse.Stop s = findStop(selectedStopId);
        src.setGeoJson(s == null ? EMPTY
                : "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
                  + s.longitude + "," + s.latitude + "]}}");
    }

    // ------------------------------------------------------------------ editing (B3)

    private void swapStop(TripResponse.Stop stop) {
        pickCandidate(stop.dayNumber, "Swap " + stop.name + " for…", c ->
                edit(api().replaceStop(trip.id, stop.id, StopRequest.from(c, null))));
    }

    private void addStop(int day) {
        pickCandidate(day, "Add to day " + day, c -> edit(api().addStop(trip.id, StopRequest.from(c, day))));
    }

    private void removeStop(TripResponse.Stop stop) {
        if (busy) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove " + stop.name + "?")
                .setMessage("The rest of day " + stop.dayNumber + " will be re-planned.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> edit(api().removeStop(trip.id, stop.id)))
                .show();
    }

    /** Loads nearby places for a day and lets the traveler choose one. */
    private void pickCandidate(int day, String title, Consumer<CandidateResponse> chosen) {
        if (busy) return;
        busy = true;
        Toast.makeText(this, "Finding places nearby…", Toast.LENGTH_SHORT).show();
        api().getCandidates(trip.id, day).enqueue(new Callback<List<CandidateResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<CandidateResponse>> call,
                                   @NonNull Response<List<CandidateResponse>> response) {
                busy = false;
                if (isDestroyed()) return;
                if (response.code() == 401) {
                    Session.expire(PlanActivity.this);
                    return;
                }
                List<CandidateResponse> list = response.body();
                if (!response.isSuccessful() || list == null) {
                    showError(ApiClient.errorMessage(response, "Could not load places (" + response.code() + ")"));
                    return;
                }
                if (list.isEmpty()) {
                    showError("No other places found nearby.");
                    return;
                }
                CharSequence[] labels = new CharSequence[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    CandidateResponse c = list.get(i);
                    labels[i] = c.name + "\n" + (c.category != null ? c.category + "  ·  " : "")
                            + distance(c.distanceMeters);
                }
                new MaterialAlertDialogBuilder(PlanActivity.this)
                        .setTitle(title)
                        .setItems(labels, (d, which) -> chosen.accept(list.get(which)))
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onFailure(@NonNull Call<List<CandidateResponse>> call, @NonNull Throwable t) {
                busy = false;
                if (!isDestroyed()) showError("Network error");
            }
        });
    }

    /** Sends an edit; the answer is the whole re-planned trip. */
    private void edit(Call<TripResponse> call) {
        if (busy) return;
        busy = true;
        call.enqueue(new Callback<TripResponse>() {
            @Override
            public void onResponse(@NonNull Call<TripResponse> c, @NonNull Response<TripResponse> response) {
                busy = false;
                if (isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    trip = response.body();
                    groupStops();
                    showDay(selectedDay);
                } else if (response.code() == 401) {
                    Session.expire(PlanActivity.this);
                } else {
                    // e.g. 422 "X doesn't fit into day 2 (opening hours or time left in the day)."
                    showError(ApiClient.errorMessage(response, "Could not change the plan (" + response.code() + ")"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<TripResponse> c, @NonNull Throwable t) {
                busy = false;
                if (!isDestroyed()) showError("Network error");
            }
        });
    }

    private ApiService api() {
        return ApiClient.getService(getApplicationContext());
    }

    private void showError(String message) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).setTextMaxLines(4).show();
    }

    private static String distance(int meters) {
        return meters < 1000 ? meters + " m away"
                : String.format(Locale.US, "%.1f km away", meters / 1000.0);
    }

    // ------------------------------------------------------------------ split screen (B4)

    @SuppressLint("ClickableViewAccessibility")  // dragging, not clicking; the handle has a description
    private void setupDivider() {
        handle.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int[] loc = new int[2];
                    root.getLocationOnScreen(loc);
                    float available = root.getHeight() - handle.getHeight();
                    if (available <= 0) return true;
                    float y = e.getRawY() - loc[1] - handle.getHeight() / 2f;
                    setSplit(y / available);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    return true;
                default:
                    return false;
            }
        });
    }

    /** Share of the height given to the list (the map gets the rest). */
    private void setSplit(float fraction) {
        listFraction = Math.max(MIN_LIST, Math.min(MAX_LIST, fraction));
        LinearLayout.LayoutParams listLp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        LinearLayout.LayoutParams mapLp = (LinearLayout.LayoutParams) mapFrame.getLayoutParams();
        listLp.weight = listFraction;
        mapLp.weight = 1 - listFraction;
        scroll.setLayoutParams(listLp);
        mapFrame.setLayoutParams(mapLp);
    }

    private void setFullScreen(boolean on) {
        fullScreenMap = on;
        scroll.setVisibility(on ? View.GONE : View.VISIBLE);
        handle.setVisibility(on ? View.GONE : View.VISIBLE);
        fullScreenButton.setText(on ? "Show list" : "Full map");
        leaveFullScreen.setEnabled(on);
    }

    // ------------------------------------------------------------------ map

    /** Shows only the selected day's markers and route on the map. */
    private void updateMap(int day) {
        if (styleRef == null || mapRef == null) return;
        List<TripResponse.Stop> stops = byDay.getOrDefault(day, new ArrayList<>());

        StringBuilder pts = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        StringBuilder coords = new StringBuilder();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (int i = 0; i < stops.size(); i++) {
            TripResponse.Stop s = stops.get(i);
            if (i > 0) { pts.append(","); coords.append(","); }
            pts.append("{\"type\":\"Feature\",\"properties\":{\"num\":\"").append(s.position)
                    .append("\",\"key\":\"").append(s.id)
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
        updateSelectedMarker();

        if (stops.size() >= 2) {
            mapRef.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));
        } else if (stops.size() == 1) {
            mapRef.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(stops.get(0).latitude, stops.get(0).longitude), 14));
        }
        loadWalkingRoute(stops);
    }

    /**
     * Asks the free OSRM walking server for the street route through the day's stops. The order is
     * already the shortest one: the backend optimises it whenever a day is planned or edited.
     */
    private void loadWalkingRoute(List<TripResponse.Stop> stops) {
        int request = ++routeRequest;
        if (stops.size() < 2) return;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < stops.size(); i++) {
            if (i > 0) path.append(";");
            path.append(stops.get(i).longitude).append(",").append(stops.get(i).latitude);
        }
        // The profile in this server's URL is always "driving"; routed-foot makes it a walking route.
        String url = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/"
                + path + "?overview=full&geometries=geojson";

        routeExecutor.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
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
                            : String.format(Locale.US, "%.1f km", meters / 1000);
                    legTexts.add("🚶 " + minutes + " min walk  ·  " + dist);
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || request != routeRequest || styleRef == null) return;
                    GeoJsonSource src = styleRef.getSourceAs("route");
                    if (src != null) src.setGeoJson(feature);
                    for (int i = 0; i < legTexts.size() && i < legViews.size(); i++) {
                        legViews.get(i).setText(legTexts.get(i));
                        legViews.get(i).setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
                // Keep the straight line if routing is unavailable.
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    // ------------------------------------------------------------------ helpers and lifecycle

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

    @Override
    protected void onDestroy() {
        routeExecutor.shutdownNow();
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
        if (trip != null) outState.putString("trip", new Gson().toJson(trip));
        outState.putInt("day", selectedDay);
        outState.putString("stop", selectedStopId);
        outState.putFloat("split", listFraction);
        outState.putBoolean("fullScreen", fullScreenMap);
    }
}

package com.wanderly.backend.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Real places with real coordinates from OpenStreetMap Nominatim (1 request/second, cached per city). */
@Component
public class GeocodingClient {

    /** openingHours is the raw OSM opening_hours tag (e.g. "Tu-Su 10:00-18:00"), or null. */
    public record Place(int id, String name, String category, double lat, double lon, String openingHours) {}

    // What we search for in each city, and the category we show for it.
    private static final String[][] SEARCHES = {
            {"museum", "Museum"}, {"art gallery", "Art"}, {"park", "Nature"},
            {"church", "Historical"}, {"monument", "Landmark"}, {"attraction", "Landmark"},
            {"cafe", "Food"}, {"restaurant", "Food"}
    };

    private final RestClient http;
    private final Map<String, List<Place>> cache = new ConcurrentHashMap<>();
    private long lastCall = 0;

    public GeocodingClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "WanderlyStudentApp/0.1 (university project)")
                .requestFactory(f)
                .build();
    }

    /** All candidate places in and around the city centre. Empty if the city isn't found. */
    public List<Place> candidates(String city) {
        String key = city.trim().toLowerCase();
        List<Place> cached = cache.get(key);
        if (cached != null) return cached;

        List<Map<String, Object>> centre = search(city, null);
        if (centre.isEmpty()) return List.of();
        double lat = Double.parseDouble((String) centre.get(0).get("lat"));
        double lon = Double.parseDouble((String) centre.get(0).get("lon"));
        String box = (lon - 0.08) + "," + (lat + 0.08) + "," + (lon + 0.08) + "," + (lat - 0.08);

        List<Place> places = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String[] s : SEARCHES) {
            for (Map<String, Object> r : search(s[0] + " in " + city, box)) {
                String name = (String) r.get("name");
                if (name == null || name.isBlank() || !seen.add(name.toLowerCase())) continue;
                places.add(new Place(places.size() + 1, name, s[1],
                        Double.parseDouble((String) r.get("lat")),
                        Double.parseDouble((String) r.get("lon")),
                        openingHours(r)));
            }
        }
        if (!places.isEmpty()) cache.put(key, places);
        return places;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> search(String q, String box) {
        try {
            throttle();
            List<Map<String, Object>> res = http.get()
                    .uri(b -> {
                        b.path("/search").queryParam("q", q)
                                .queryParam("format", "json")
                                .queryParam("extratags", 1)
                                .queryParam("limit", box == null ? 1 : 30);
                        if (box != null) b.queryParam("viewbox", box).queryParam("bounded", 1);
                        return b.build();
                    })
                    .retrieve()
                    .body(List.class);
            return res == null ? List.of() : res;
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static String openingHours(Map<String, Object> r) {
        if (!(r.get("extratags") instanceof Map<?, ?> tags)) return null;
        Object oh = ((Map<String, Object>) tags).get("opening_hours");
        if (!(oh instanceof String s) || s.isBlank()) return null;
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    /** Nominatim's usage policy: at most one request per second. */
    private synchronized void throttle() throws InterruptedException {
        long wait = 1100 - (System.currentTimeMillis() - lastCall);
        if (wait > 0) Thread.sleep(wait);
        lastCall = System.currentTimeMillis();
    }
}
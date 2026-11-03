package com.wanderly.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real places with real coordinates from OpenStreetMap Nominatim (at most 1 request/second, shared by
 * every user). Each search (city centre, "museum in Paris", ...) is cached on its own, and only when it
 * succeeded, so a lookup cut short by the deadline or a failed request is simply retried by the next trip.
 */
@Component
public class GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(GeocodingClient.class);

    /** openingHours is the raw OSM opening_hours tag (e.g. "Tu-Su 10:00-18:00"), or null. */
    public record Place(int id, String name, String category, double lat, double lon, String openingHours) {}

    public record Coordinates(double lat, double lon) {}

    /** One search result, without the id (ids are numbered per trip). */
    private record Found(String name, double lat, double lon, String openingHours) {}

    private record Cached(List<Found> results, Instant at) {}

    // What we search for in each city, and the category we show for it. Mixed so a lookup
    // cut short by the deadline still covers several kinds of places.
    private static final String[][] SEARCHES = {
            {"museum", "Museum"}, {"park", "Nature"}, {"attraction", "Landmark"},
            {"restaurant", "Food"}, {"church", "Historical"}, {"cafe", "Food"},
            {"monument", "Landmark"}, {"art gallery", "Art"}
    };

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int CACHE_MAX_SEARCHES = 2000;  // ~200 cities
    private static final int MAX_NAME = 200;             // trip_stops.name
    /** Nominatim's usage policy: an absolute maximum of 1 request per second. */
    private static final long MIN_GAP_MS = 1100;
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(6);

    private final RestClient http;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private long nextSlot = 0;  // epoch millis of the next free request slot; guarded by this

    public GeocodingClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "WanderlyStudentApp/0.1 (university project)")
                .requestFactory(f)
                .build();
    }

    /**
     * Candidate places in and around the city centre; empty if the city isn't found.
     * {@code centre} is used when the app already knows it (from autocomplete), saving one request.
     * Searches not cached yet are only sent if their turn comes before the deadline, so a cold city
     * (or a busy server) may return fewer places.
     */
    public List<Place> candidates(String city, Coordinates centre, Instant deadline) {
        if (centre == null) {
            List<Found> found = search(city, null, 1, deadline);
            if (found == null || found.isEmpty()) return List.of();
            centre = new Coordinates(found.get(0).lat(), found.get(0).lon());
        }
        // Fixed-point: Double.toString would write e.g. 5.0E-4 near the 0° lines, which Nominatim rejects.
        String box = String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f",
                centre.lon() - 0.08, centre.lat() + 0.08, centre.lon() + 0.08, centre.lat() - 0.08);

        List<Place> places = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int missing = 0;
        for (String[] s : SEARCHES) {
            List<Found> results = search(s[0] + " in " + city, box, 30, deadline);
            if (results == null) {
                missing++;
                continue;
            }
            for (Found r : results) {
                if (!seen.add(r.name().toLowerCase(Locale.ROOT))) continue;
                places.add(new Place(places.size() + 1, r.name(), s[1], r.lat(), r.lon(), r.openingHours()));
            }
        }
        if (missing > 0) {
            log.warn("{}: {} of {} place searches failed or ran out of time; the next trip retries them",
                    city, missing, SEARCHES.length);
        }
        return places;
    }

    /**
     * Where a named place is, e.g. "Louvre, Paris, France". Empty when OSM doesn't know it;
     * throws {@link IllegalStateException} when Nominatim can't be reached in time.
     */
    public Optional<Coordinates> locate(String query, Instant deadline) {
        List<Found> found = search(query, null, 1, deadline);
        if (found == null) throw new IllegalStateException("Place lookup unavailable");
        return found.stream().findFirst().map(f -> new Coordinates(f.lat(), f.lon()));
    }

    /** Cached results, or a fresh search; null when it failed or its turn came too late. */
    private List<Found> search(String q, String box, int limit, Instant deadline) {
        String key = q.trim().toLowerCase(Locale.ROOT) + (box == null ? "|point" : "");
        Cached cached = cache.get(key);
        if (cached != null) {
            if (cached.at().plus(CACHE_TTL).isAfter(Instant.now())) return cached.results();
            cache.remove(key, cached);
        }
        List<Found> results = fetch(q, box, limit, deadline);
        if (results != null) put(key, results);
        return results;
    }

    private void put(String key, List<Found> results) {
        if (cache.size() >= CACHE_MAX_SEARCHES) {
            cache.entrySet().stream()
                    .min(Comparator.comparing((Map.Entry<String, Cached> e) -> e.getValue().at()))
                    .ifPresent(oldest -> cache.remove(oldest.getKey()));
        }
        cache.put(key, new Cached(results, Instant.now()));
    }

    @SuppressWarnings("unchecked")
    private List<Found> fetch(String q, String box, int limit, Instant deadline) {
        try {
            if (!waitForSlot(deadline)) return null;
            List<Map<String, Object>> res = http.get()
                    .uri(b -> {
                        b.path("/search").queryParam("q", q)
                                .queryParam("format", "json")
                                .queryParam("extratags", 1)
                                .queryParam("limit", limit);
                        if (box != null) b.queryParam("viewbox", box).queryParam("bounded", 1);
                        return b.build();
                    })
                    .retrieve()
                    .body(List.class);
            List<Found> found = new ArrayList<>();
            for (Map<String, Object> r : res == null ? List.<Map<String, Object>>of() : res) {
                String name = (String) r.get("name");
                // Point lookups keep unnamed results: only their coordinates matter.
                if (box != null && (name == null || name.isBlank())) continue;
                if (name != null && name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);
                found.add(new Found(name, Double.parseDouble((String) r.get("lat")),
                        Double.parseDouble((String) r.get("lon")), openingHours(r)));
            }
            return List.copyOf(found);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("Nominatim search '{}' failed: {}", q, e.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String openingHours(Map<String, Object> r) {
        if (!(r.get("extratags") instanceof Map<?, ?> tags)) return null;
        Object oh = ((Map<String, Object>) tags).get("opening_hours");
        if (!(oh instanceof String s) || s.isBlank()) return null;
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    /**
     * Books the next free 1-request-per-second slot and sleeps until it, without holding the lock,
     * so concurrent trips queue fairly. Gives up (false) when that slot comes after the caller's
     * deadline, instead of making the traveler wait behind other users' lookups.
     */
    boolean waitForSlot(Instant deadline) throws InterruptedException {
        long slot;
        synchronized (this) {
            long now = System.currentTimeMillis();
            slot = Math.max(now, nextSlot);
            if (slot > deadline.toEpochMilli()) return false;
            nextSlot = slot + MIN_GAP_MS;
        }
        long wait = slot - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        return true;
    }
}

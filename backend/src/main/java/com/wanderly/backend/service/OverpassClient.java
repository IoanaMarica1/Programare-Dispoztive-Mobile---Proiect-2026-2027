package com.wanderly.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Places around a point from OpenStreetMap, through the Overpass API. Unlike Nominatim (used for trips) it can
 * ask for "everything of these kinds within 2 km" in one request, which is what Surprise Me needs in under 5 s.
 * Two public servers are tried in turn; the caller's deadline bounds the whole lookup.
 */
@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    /** One named place. {@code ref} is "node/123", "way/45" or "relation/6". */
    public record Element(String ref, String name, double lat, double lon, Map<String, String> tags) {}

    private static final List<String> ENDPOINTS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter");

    // Kept short on purpose: the whole suggestion must arrive in under 5 s, and what is left after
    // Overpass gives up goes to the Nominatim fallback (see SurpriseService).
    private static final Duration FIRST_ATTEMPT = Duration.ofMillis(2400);
    private static final Duration SECOND_ATTEMPT = Duration.ofMillis(1000);
    /** The second server is only tried when the first one failed fast (busy, refused), not when it was slow. */
    private static final Duration FAST_FAILURE = Duration.ofMillis(1000);
    /** A second server is only worth trying if it gets at least this long. */
    private static final Duration MIN_ATTEMPT = Duration.ofMillis(600);
    /** After both servers failed, searches skip Overpass for this long and go straight to the fallback. */
    private static final Duration COOL_DOWN = Duration.ofSeconds(20);
    private static final int MAX_NAME = 200;
    /** Overpass can't sort by distance, so a dense centre needs room: the nearest place must not be cut off. */
    private static final int MAX_ELEMENTS = 600;

    private volatile Instant downUntil = Instant.EPOCH;

    /**
     * Named places within {@code radius} metres of the point that match any of the tag filters
     * (key → accepted values, ";"-separated lists such as "bar;pub" included).
     * Throws {@link IllegalStateException} when no server answers in time.
     */
    public List<Element> around(double lat, double lon, int radius, Map<String, Set<String>> filters,
                                Instant deadline) {
        // A busy Overpass would eat the whole time budget of every tap: let the caller use its fallback at once.
        if (Instant.now().isBefore(downUntil)) throw new IllegalStateException("Overpass recently unavailable");
        return parse(post(buildAroundQuery(lat, lon, radius, filters), deadline, true));
    }

    /** One place by its reference; empty when OpenStreetMap no longer has it. Throws like {@link #around}. */
    public Optional<Element> lookup(String ref, Instant deadline) {
        return parse(post(buildLookupQuery(ref), deadline, false)).stream().findFirst();
    }

    static String buildAroundQuery(double lat, double lon, int radius, Map<String, Set<String>> filters) {
        StringBuilder q = new StringBuilder("[out:json][timeout:4];(");
        // A new TreeMap keeps the query the same for the same mood (and so easy to compare in tests).
        for (Map.Entry<String, Set<String>> f : new java.util.TreeMap<>(filters).entrySet()) {
            q.append(String.format(Locale.ROOT, "nwr(around:%d,%.5f,%.5f)[\"name\"][\"%s\"~\"(^|;)(%s)(;|$)\"];",
                    radius, lat, lon, f.getKey(), String.join("|", new TreeSet<>(f.getValue()))));
        }
        return q.append(");out center tags ").append(MAX_ELEMENTS).append(';').toString();
    }

    /** {@code ref} must already match SurprisePlaceResponse.ID_PATTERN; it is put into the query as is. */
    static String buildLookupQuery(String ref) {
        String[] p = ref.split("/");
        return "[out:json][timeout:4];" + p[0] + "(" + p[1] + ");out center tags;";
    }

    private Map<String, Object> post(String query, Instant deadline, boolean tripsCoolDown) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", query);

        Instant began = Instant.now();
        boolean attempted = false;
        for (int i = 0; i < ENDPOINTS.size(); i++) {
            Duration left = Duration.between(Instant.now(), deadline);
            if (left.compareTo(MIN_ATTEMPT) < 0) break;
            // The first server was slow, not refused: the second would most likely be slow too. A check-in
            // lookup (no fallback, longer deadline) still tries it, and splits its time between the two.
            if (i > 0 && tripsCoolDown && Duration.between(began, Instant.now()).compareTo(FAST_FAILURE) > 0) break;
            Duration cap = !tripsCoolDown ? (i == 0 ? left.dividedBy(2) : left)
                    : i == 0 ? FIRST_ATTEMPT : SECOND_ATTEMPT;
            attempted = true;
            try {
                Map<String, Object> res = send(ENDPOINTS.get(i), form, left.compareTo(cap) > 0 ? cap : left);
                // Overpass answers 200 with a "remark" when it gave up halfway: that data is incomplete.
                if (res != null && !(res.get("remark") instanceof String remark && remark.contains("error"))) {
                    return res;
                }
                log.debug("Overpass {} gave an incomplete answer: {}", ENDPOINTS.get(i), res == null ? null : res.get("remark"));
            } catch (Exception e) {
                log.debug("Overpass {} failed: {}", ENDPOINTS.get(i), e.toString());
            }
        }
        // Only a real failure of an area search starts the cool-down: not a caller whose time had already
        // run out, and not a check-in lookup (which has no fallback and a longer deadline).
        if (attempted && tripsCoolDown) downUntil = Instant.now().plus(COOL_DOWN);
        throw new IllegalStateException("Overpass unavailable");
    }

    /** One request to one server; tests replace it. */
    @SuppressWarnings("unchecked")
    Map<String, Object> send(String endpoint, MultiValueMap<String, String> form, Duration timeout) {
        return client(timeout).post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
    }

    /** Named elements with a position; ways and relations use the centre Overpass computes for them. */
    @SuppressWarnings("unchecked")
    static List<Element> parse(Map<String, Object> response) {
        List<Element> found = new ArrayList<>();
        if (!(response.get("elements") instanceof List<?> elements)) return found;

        for (Object o : elements) {
            if (!(o instanceof Map<?, ?> raw)) continue;
            Map<String, Object> e = (Map<String, Object>) raw;
            if (!(e.get("tags") instanceof Map<?, ?> rawTags) || !(e.get("id") instanceof Number id)) continue;

            Map<String, String> tags = new LinkedHashMap<>();
            for (Map.Entry<?, ?> t : rawTags.entrySet()) tags.put(String.valueOf(t.getKey()), String.valueOf(t.getValue()));

            String name = tags.getOrDefault("name:en", tags.get("name"));
            if (name == null || name.isBlank()) continue;
            name = name.trim();
            if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);

            Map<String, Object> at = e.get("center") instanceof Map<?, ?> c ? (Map<String, Object>) c : e;
            if (!(at.get("lat") instanceof Number lat) || !(at.get("lon") instanceof Number lon)) continue;
            if (!(e.get("type") instanceof String type)) continue;

            found.add(new Element(type + "/" + id.longValue(), name, lat.doubleValue(), lon.doubleValue(),
                    Map.copyOf(tags)));
        }
        return found;
    }

    private static RestClient client(Duration readTimeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofMillis(1500));
        f.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(f)
                .defaultHeader("User-Agent", "WanderlyStudentApp/0.1 (university project)")
                .build();
    }
}

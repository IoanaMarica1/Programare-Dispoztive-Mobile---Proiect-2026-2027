package com.wanderly.backend.service;

import com.wanderly.backend.dto.surprise.CheckInRequest;
import com.wanderly.backend.dto.surprise.SurprisePlaceResponse;
import com.wanderly.backend.dto.surprise.SurpriseRequest;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.model.Mood;
import com.wanderly.backend.service.GeocodingClient.Address;
import com.wanderly.backend.service.GeocodingClient.Coordinates;
import com.wanderly.backend.service.OverpassClient.Element;
import com.wanderly.backend.service.SurpriseRanker.Candidate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Story E1: one tap, the best place within 2 km that fits the mood and is open now.
 *
 * <p>Not @Transactional: the map lookups take seconds, so no database connection is held while they run.
 */
@Service
public class SurpriseService {

    /** The brief asks for a result in under 5 seconds; the rest goes to the network round trip. */
    private static final Duration SEARCH_BUDGET = Duration.ofMillis(4200);
    private static final Duration LOOKUP_BUDGET = Duration.ofSeconds(8);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int CACHE_MAX_AREAS = 500;
    /**
     * Places are fetched around the centre of a ~110 m square and then measured from the traveler's real
     * position, so people standing in the same street share one lookup ("Another one" costs nothing).
     * The margin covers the distance from the centre to the square's farthest corner.
     */
    private static final double CELL = 1000.0;
    private static final int CELL_MARGIN_METERS = 100;

    /** The Nominatim fallback covers only a few kinds of places, so it is trusted for less time. */
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofMinutes(2);
    private static final int FALLBACK_SEARCHES = 3;

    private record Cached(List<Element> places, Instant at, Duration ttl) {}

    private final OverpassClient overpass;
    private final GeocodingClient geocoder;
    private final VisitService visitService;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public SurpriseService(OverpassClient overpass, GeocodingClient geocoder, VisitService visitService) {
        this.overpass = overpass;
        this.geocoder = geocoder;
        this.visitService = visitService;
    }

    public SurprisePlaceResponse suggest(SurpriseRequest request) {
        ZoneId zone = zone(request.timeZone());
        Mood mood = request.mood();
        double lat = request.latitude();
        double lon = request.longitude();
        Set<String> skip = request.exclude() == null ? Set.of() : new HashSet<>(request.exclude());

        List<Element> places = placesAround(mood, lat, lon);
        List<Candidate> ranked = SurpriseRanker.rank(places, mood, lat, lon, ZonedDateTime.now(zone));
        List<Candidate> remaining = ranked.stream().filter(c -> !skip.contains(c.place().ref())).toList();

        if (remaining.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, skip.isEmpty()
                    ? "Nothing open within 2 km fits this mood right now. Try another mood."
                    : "That's everything open nearby for this mood. Try another mood.");
        }
        return toResponse(mood, remaining.get(0), remaining.size() > 1);
    }

    /**
     * Checks the traveler in at a suggested place. The server looks the place up itself (the app only says
     * which one), so a check-in can't be faked by sending made-up coordinates for the place.
     */
    public VisitResultResponse checkIn(String email, CheckInRequest request) {
        Element place;
        try {
            place = overpass.lookup(request.placeId(), Instant.now().plus(LOOKUP_BUDGET))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "That place is no longer on the map."));
        } catch (IllegalStateException e) {
            throw unavailable("Could not check your location right now. Try again.");
        }

        VisitService.requireNear(place.name(), new Coordinates(place.lat(), place.lon()),
                request.latitude(), request.longitude());

        Address address;
        try {
            address = geocoder.reverse(place.lat(), place.lon(), Instant.now().plus(LOOKUP_BUDGET))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Could not tell which town " + place.name() + " is in."));
        } catch (IllegalStateException e) {
            throw unavailable("Could not check your location right now. Try again.");
        }

        return visitService.recordSurpriseVisit(email, place.name(), cut(address.city(), 100),
                cut(address.country(), 100), new Coordinates(place.lat(), place.lon()));
    }

    private List<Element> placesAround(Mood mood, double lat, double lon) {
        double cellLat = Math.round(lat * CELL) / CELL;
        double cellLon = Math.round(lon * CELL) / CELL;
        String key = mood + ":" + String.format(Locale.ROOT, "%.3f,%.3f", cellLat, cellLon);

        Cached cached = cache.get(key);
        if (cached != null) {
            if (cached.at().plus(cached.ttl()).isAfter(Instant.now())) return cached.places();
            cache.remove(key, cached);
        }

        int radius = SurpriseRanker.RADIUS_METERS + CELL_MARGIN_METERS;
        Instant deadline = Instant.now().plus(SEARCH_BUDGET);
        List<Element> places;
        try {
            places = overpass.around(cellLat, cellLon, radius, SurpriseRanker.filters(mood), deadline);
            // An empty answer is cached too: a quiet street won't have a place by the next tap.
            put(key, places, CACHE_TTL);
        } catch (IllegalStateException e) {
            // Overpass is busy or slow: settle for what Nominatim can find in the time that is left.
            places = fallback(mood, cellLat, cellLon, radius, deadline);
            put(key, places, FALLBACK_CACHE_TTL);
        }
        return places;
    }

    /** The few best-fitting kinds of place from Nominatim; 503 when not even one search got through. */
    private List<Element> fallback(Mood mood, double lat, double lon, int radius, Instant deadline) {
        Map<String, Element> byRef = new java.util.LinkedHashMap<>();
        boolean answered = false;
        for (String term : SurpriseRanker.fallbackTerms(mood, FALLBACK_SEARCHES)) {
            List<Element> found = geocoder.nearby(term, lat, lon, radius, deadline);
            if (found == null) continue;
            answered = true;
            found.forEach(p -> byRef.putIfAbsent(p.ref(), p));
        }
        if (!answered) throw unavailable("Could not look for places right now. Try again.");
        return List.copyOf(byRef.values());
    }

    private void put(String key, List<Element> places, Duration ttl) {
        if (cache.size() >= CACHE_MAX_AREAS) {
            cache.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().at()))
                    .ifPresent(oldest -> cache.remove(oldest.getKey()));
        }
        cache.put(key, new Cached(places, Instant.now(), ttl));
    }

    private static SurprisePlaceResponse toResponse(Mood mood, Candidate c, boolean moreAvailable) {
        Element p = c.place();
        return new SurprisePlaceResponse(
                p.ref(), p.name(), capitalize(c.rule().label()), p.lat(), p.lon(),
                c.distanceMeters(), c.walkingMinutes(), SurpriseRanker.why(mood, c), c.openNote(),
                SurpriseRanker.photoUrl(p.tags()), SurpriseRanker.address(p.tags()),
                SurpriseRanker.website(p.tags()), moreAvailable);
    }

    /** The phone's own zone, falling back to the server's when the app doesn't send one. */
    private static ZoneId zone(String id) {
        if (id == null || id.isBlank()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(id.trim());
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone.");
        }
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}

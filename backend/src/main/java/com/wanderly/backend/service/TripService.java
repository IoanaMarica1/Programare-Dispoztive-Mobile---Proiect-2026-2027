package com.wanderly.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripStop;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.TripRepository;
import com.wanderly.backend.service.GeocodingClient.Coordinates;
import com.wanderly.backend.service.GeocodingClient.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    /** The phone stops waiting after 60 s, so generation must finish (or fail) well before that. */
    private static final Duration TIME_BUDGET = Duration.ofSeconds(50);
    /**
     * New place searches are only sent during this part of the budget; the rest is left for Gemini.
     * A cold city needs ~9 searches of 2-3 s each, so the first trip there may get fewer places
     * (the searches it skipped are sent, and cached, by the next trip).
     */
    private static final Duration PLACES_BUDGET = Duration.ofSeconds(20);

    static final int MIN_STOP_MINUTES = 15;
    static final int MAX_STOP_MINUTES = 480;
    /** "09:30", but also "9:30" and "09:30:00", which the model sometimes returns. */
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("H:mm[:ss]");

    /**
     * Gemini gets a shortlist, not every place found: a shorter prompt answers noticeably faster.
     * About 1.5 candidates per stop leaves it room to group nearby places and respect opening hours.
     */
    static final int MIN_SHORTLIST = 30;
    static final int MAX_SHORTLIST = 100;

    /** Which place categories suit each interest. Food places are always offered too, for meals. */
    private static final Map<Interest, Set<String>> CATEGORIES = Map.of(
            Interest.HISTORY, Set.of("Museum", "Historical", "Landmark"),
            Interest.ART, Set.of("Art", "Museum"),
            Interest.NATURE, Set.of("Nature"),
            Interest.FOOD, Set.of("Food"),
            Interest.NIGHTLIFE, Set.of("Food"),
            Interest.ADVENTURE, Set.of("Nature", "Landmark"));

    private final TripRepository tripRepository;
    private final UserService userService;
    private final PreferencesService preferencesService;
    private final GeminiClient gemini;
    private final GeocodingClient geocoder;
    private final JsonMapper json = JsonMapper.builder().build();

    public TripService(TripRepository tripRepository, UserService userService,
                       PreferencesService preferencesService, GeminiClient gemini,
                       GeocodingClient geocoder) {
        this.tripRepository = tripRepository;
        this.userService = userService;
        this.preferencesService = preferencesService;
        this.gemini = gemini;
        this.geocoder = geocoder;
    }

    /** Gemini only picks places by id and schedules them. Boxed so a missing value skips one stop, not the plan. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiStop(Integer day, Integer id, String start, Integer durationMinutes, String description) {}
    record Picked(AiStop ai, Place place, LocalTime start) {}

    /**
     * Not @Transactional on purpose: the external calls take seconds, and a transaction here would keep a
     * database connection checked out the whole time. Each repository call runs in its own short transaction.
     */
    public TripResponse generate(String email, GenerateTripRequest req) {
        Instant began = Instant.now();
        Instant deadline = began.plus(TIME_BUDGET);
        User user = userService.getByEmail(email);
        int days = req.days();
        String destination = req.destination().trim();
        String country = req.country() == null || req.country().isBlank() ? null : req.country().trim();
        var prefs = preferencesService.getPreferences(email).orElse(null);

        // 1. Real places for this city (each search is cached once it succeeds), shortlisted for Gemini.
        Coordinates centre = req.latitude() != null && req.longitude() != null
                ? new Coordinates(req.latitude(), req.longitude()) : null;
        List<Place> found = geocoder.candidates(destination, centre, began.plus(PLACES_BUDGET));
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No places found for " + destination);
        }
        List<Place> places = shortlist(found, prefs == null ? Set.of() : prefs.interests(), days,
                stopsPerDay(prefs == null ? Set.of() : prefs.paces()));
        Map<Integer, Place> byId = places.stream().collect(Collectors.toMap(Place::id, Function.identity()));
        String list = places.stream()
                .map(p -> p.id() + ". " + p.name() + " (" + p.category() + ")"
                        + (p.openingHours() != null ? " [open: " + p.openingHours() + "]" : ""))
                .collect(Collectors.joining("\n"));
        Instant placesDone = Instant.now();

        String interests = prefs == null ? "general sightseeing" : prefs.interests().toString();
        // Pace is an optional wizard step, so it may be empty.
        String pace = prefs == null || prefs.paces().isEmpty() ? "balanced" : prefs.paces().toString();
        String modes = prefs == null ? "solo" : prefs.travelModes().toString();

        String prompt = """
                You are a travel planner. Plan a %d-day trip to %s starting on %s (day 1 is a %s).
                Daily window: %s to %s. Travel mode: %s. Interests: %s. Pace: %s
                (relaxed = 3 stops/day, balanced = 4-5, packed = 6-7).
                Choose ONLY from these real places (use their id):
                %s
                Rules:
                - Prefer places matching the interests; include at most one Food stop per meal time.
                - Group nearby places on the same day.
                - Every stop starts and ends inside the daily window, when the place is open on that
                  weekday (respect the [open: ...] hours when given, OSM format).
                - Stops on the same day must not overlap; leave time to walk between them.
                - "durationMinutes" is a realistic visit length between %d and %d.
                - Never use the same id twice.
                - "description": one short sentence (max 120 characters) on why it's worth visiting.
                Return ONLY JSON: {"stops":[{"day":1,"id":12,"start":"HH:mm","durationMinutes":60,"description":"..."}]}
                """.formatted(days, destination, req.startDate(), req.startDate().getDayOfWeek(),
                req.dayStart(), req.dayEnd(), modes, interests, pace, list,
                MIN_STOP_MINUTES, MAX_STOP_MINUTES);

        // 2. Gemini schedules them.
        String answer;
        List<AiStop> aiStops;
        try {
            answer = gemini.generateJson(prompt, deadline);
            aiStops = parseStops(answer);
        } catch (RuntimeException e) {
            log.error("Plan generation failed", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Plan generation failed", e);
        }
        Instant geminiDone = Instant.now();

        // 3. Keep only stops that fit the trip, then number them per day.
        List<Picked> picked = validStops(aiStops, byId, req.startDate(), days, req.dayStart(), req.dayEnd());

        Trip trip = Trip.builder()
                .user(user)
                .destination(destination)
                .country(country)
                .latitude(0.0)
                .longitude(0.0)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .dayStart(req.dayStart())
                .dayEnd(req.dayEnd())
                .build();

        Map<Integer, Integer> nextPos = new HashMap<>();
        for (Picked x : picked) {
            int pos = nextPos.merge(x.ai().day(), 1, Integer::sum);
            trip.addStop(stop(x.ai().day(), pos, x.place().name(), x.place().category(),
                    x.place().lat(), x.place().lon(), x.start(), x.ai().durationMinutes(),
                    shorten(x.ai().description()), x.place().openingHours()));
        }
        if (trip.getStops().isEmpty()) {
            log.warn("No usable stops in the model's answer: {}",
                    answer == null || answer.length() <= 2000 ? answer : answer.substring(0, 2000) + "...");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Plan generation returned no usable stops");
        }
        trip.setCenter(
                trip.getStops().stream().mapToDouble(TripStop::getLatitude).average().orElse(0),
                trip.getStops().stream().mapToDouble(TripStop::getLongitude).average().orElse(0));

        log.info("Plan for {} days: places {} ms ({} found, {} sent), gemini {} ms, kept {} of {} stops",
                days, Duration.between(began, placesDone).toMillis(), found.size(), places.size(),
                Duration.between(placesDone, geminiDone).toMillis(), picked.size(), aiStops.size());

        Trip saved = tripRepository.save(trip);
        return toResponse(saved);
    }

    /** The most stops a day can have for the chosen paces (see the prompt); balanced when none. */
    static int stopsPerDay(Set<Pace> paces) {
        if (paces.contains(Pace.PACKED)) return 7;
        if (paces.contains(Pace.BALANCED) || paces.isEmpty()) return 5;
        return 3;
    }

    /**
     * Up to ~1.5 candidates per possible stop: first a couple of Food places per day for meals, then
     * places matching the interests, then anything else, taking categories in turn so the plan stays
     * varied. Nominatim's own ranking decides the order inside a category.
     */
    static List<Place> shortlist(List<Place> places, Set<Interest> interests, int days, int stopsPerDay) {
        int limit = Math.clamp(Math.round(days * stopsPerDay * 1.5f), MIN_SHORTLIST, MAX_SHORTLIST);
        if (places.size() <= limit) return places;

        Map<String, Deque<Place>> byCategory = new LinkedHashMap<>();
        for (Place p : places) byCategory.computeIfAbsent(p.category(), c -> new ArrayDeque<>()).add(p);
        Set<String> wanted = new HashSet<>();
        for (Interest i : interests) wanted.addAll(CATEGORIES.getOrDefault(i, Set.of()));
        if (wanted.isEmpty()) wanted.addAll(byCategory.keySet());

        List<Place> picked = new ArrayList<>();
        takeInTurn(byCategory, Set.of("Food"), Math.min(days * 2, limit), picked);
        takeInTurn(byCategory, wanted, limit - picked.size(), picked);
        takeInTurn(byCategory, byCategory.keySet(), limit - picked.size(), picked);
        picked.sort(Comparator.comparingInt(Place::id));
        return picked;
    }

    private static void takeInTurn(Map<String, Deque<Place>> byCategory, Set<String> categories,
                                   int count, List<Place> out) {
        boolean tookAny = true;
        while (count > 0 && tookAny) {
            tookAny = false;
            for (Map.Entry<String, Deque<Place>> e : byCategory.entrySet()) {
                if (count == 0) break;
                if (!categories.contains(e.getKey()) || e.getValue().isEmpty()) continue;
                out.add(e.getValue().poll());
                count--;
                tookAny = true;
            }
        }
    }

    /**
     * Reads the model's stops one by one, so a single malformed stop is skipped instead of failing the plan.
     * Throws only when the text isn't JSON at all.
     */
    List<AiStop> parseStops(String text) {
        JsonNode root = json.readTree(stripCodeFence(text));
        JsonNode array = root.isArray() ? root : root.path("stops");
        List<AiStop> stops = new ArrayList<>();
        if (!array.isArray()) return stops;
        for (JsonNode node : array) {
            try {
                stops.add(json.treeToValue(node, AiStop.class));
            } catch (JacksonException e) {
                log.debug("Skipping malformed stop {}: {}", node, e.getOriginalMessage());
            }
        }
        return stops;
    }

    /**
     * Stops that name a known place once, fall on a trip day, last a realistic time, fit the daily window,
     * respect the place's opening hours and don't overlap another stop that day. Sorted by day and time.
     */
    static List<Picked> validStops(List<AiStop> stops, Map<Integer, Place> byId, LocalDate startDate,
                                   int days, LocalTime dayStart, LocalTime dayEnd) {
        int windowStart = minuteOfDay(dayStart);
        int windowEnd = minuteOfDay(dayEnd);

        List<Picked> candidates = new ArrayList<>();
        Map<String, Integer> dropped = new LinkedHashMap<>();
        for (AiStop s : stops) {
            String reason = null;
            Place p = null;
            LocalTime start = null;
            if (s == null || s.id() == null || s.day() == null || s.durationMinutes() == null) {
                reason = "missing field";
            } else if ((p = byId.get(s.id())) == null) {
                reason = "unknown place";
            } else if (s.day() < 1 || s.day() > days) {
                reason = "outside trip days";
            } else if (s.durationMinutes() < MIN_STOP_MINUTES || s.durationMinutes() > MAX_STOP_MINUTES) {
                reason = "unrealistic duration";
            } else if ((start = parseTime(s.start())) == null) {
                reason = "bad start time";
            } else {
                // Plain minutes, so a visit running past midnight can't wrap round and look early.
                int from = minuteOfDay(start);
                int to = from + s.durationMinutes();
                var day = startDate.plusDays(s.day() - 1).getDayOfWeek();
                if (from < windowStart || to > windowEnd) {
                    reason = "outside daily window";
                } else if (OpeningHours.check(p.openingHours(), day, from, to) == OpeningHours.Result.CLOSED) {
                    reason = "place closed";
                }
            }
            if (reason != null) {
                dropped.merge(reason, 1, Integer::sum);
            } else {
                candidates.add(new Picked(s, p, start));
            }
        }
        candidates.sort(Comparator.comparingInt((Picked x) -> x.ai().day()).thenComparing(Picked::start));

        // In time order, keep the first of any overlapping stops and the first use of each place.
        List<Picked> kept = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        Map<Integer, Integer> dayEndsAt = new HashMap<>();
        for (Picked x : candidates) {
            int from = minuteOfDay(x.start());
            if (used.contains(x.ai().id())) {
                dropped.merge("duplicate place", 1, Integer::sum);
            } else if (from < dayEndsAt.getOrDefault(x.ai().day(), 0)) {
                dropped.merge("overlaps previous stop", 1, Integer::sum);
            } else {
                used.add(x.ai().id());
                dayEndsAt.put(x.ai().day(), from + x.ai().durationMinutes());
                kept.add(x);
            }
        }
        if (!dropped.isEmpty()) log.info("Dropped stops from the model: {}", dropped);
        return kept;
    }

    private static LocalTime parseTime(String text) {
        if (text == null) return null;
        try {
            return LocalTime.parse(text.trim(), HOUR_MINUTE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The model is asked for bare JSON, but some answers still come wrapped in a ```json fence. */
    private static String stripCodeFence(String text) {
        if (text == null) return "";
        String t = text.strip();
        if (!t.startsWith("```")) return t;
        int firstLine = t.indexOf('\n');
        int closing = t.lastIndexOf("```");
        return firstLine < 0 || closing <= firstLine ? t : t.substring(firstLine + 1, closing).strip();
    }

    private static int minuteOfDay(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private TripStop stop(int day, int pos, String name, String category,
                          double lat, double lon, LocalTime start, int minutes,
                          String description, String openingHours) {
        return TripStop.builder()
                .dayNumber(day).position(pos).name(name).category(category)
                .latitude(lat).longitude(lon).startTime(start).durationMinutes(minutes)
                .description(description).openingHours(openingHours)
                .build();
    }

    private static String shorten(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        return s.length() > 300 ? s.substring(0, 297) + "..." : s;
    }

    private TripResponse toResponse(Trip t) {
        List<TripResponse.StopResponse> stops = t.getStops().stream()
                .map(s -> new TripResponse.StopResponse(
                        s.getDayNumber(), s.getPosition(), s.getName(), s.getCategory(),
                        s.getLatitude(), s.getLongitude(), s.getStartTime(), s.getDurationMinutes(),
                        s.getDescription(), s.getOpeningHours()))
                .toList();
        return new TripResponse(t.getPublicId(), t.getDestination(), t.getCountry(),
                t.getLatitude(), t.getLongitude(), t.getStartDate(), t.getEndDate(),
                t.getDayStart(), t.getDayEnd(), stops);
    }
}

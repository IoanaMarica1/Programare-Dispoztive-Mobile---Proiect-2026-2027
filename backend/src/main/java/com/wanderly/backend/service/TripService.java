package com.wanderly.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wanderly.backend.dto.trip.CandidateResponse;
import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.StopRequest;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.model.Interest;
import com.wanderly.backend.dto.common.PageResponse;
import com.wanderly.backend.dto.trip.TripSummaryResponse;
import com.wanderly.backend.model.Pace;
import org.springframework.data.domain.Pageable;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripStop;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.TripRepository;
import com.wanderly.backend.service.DayPlanner.Timed;
import com.wanderly.backend.service.GeocodingClient.Coordinates;
import com.wanderly.backend.service.GeocodingClient.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
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
    private static final Duration CANDIDATES_BUDGET = Duration.ofSeconds(15);

    static final int MIN_STOP_MINUTES = 15;
    static final int MAX_STOP_MINUTES = 480;
    /** A day edited by hand may hold a little more than the packed pace. */
    static final int MAX_STOPS_PER_DAY = DayPlanner.MAX_EXACT;
    /** How many stops a day left empty by the model is filled with (plus lunch). */
    static final int FILL_STOPS = 3;
    private static final int MAX_CANDIDATES = 40;
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

    /** Visit length for stops the model didn't time (filled days, stops added by hand). */
    private static final Map<String, Integer> DEFAULT_MINUTES = Map.of(
            "Museum", 90, "Art", 75, "Historical", 45, "Landmark", 45, "Nature", 60, "Food", 60);

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

    /** Gemini picks places by id and suggests a day, time and length. Boxed so a missing value skips one stop, not the plan. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiStop(Integer day, Integer id, String start, Integer durationMinutes, String description) {}
    /** A stop that passed the basic checks; start only keeps the model's order (null sorts last). */
    record Picked(AiStop ai, Place place, LocalTime start) {}
    /** One stop of the final plan, before it becomes an entity. start is minutes from midnight. */
    record PlannedStop(int day, int position, Place place, int start, int minutes, String description) {}

    // ---------------------------------------------------------------- generation

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
        Set<Pace> paces = chosenPaces(req.pace(), prefs == null ? Set.of() : prefs.paces());
        int perDay = stopsPerDay(paces);

        // 1. Real places for this city (each search is cached once it succeeds), shortlisted for Gemini.
        Coordinates centre = req.latitude() != null && req.longitude() != null
                ? new Coordinates(req.latitude(), req.longitude()) : null;
        List<Place> found = geocoder.candidates(destination, centre, began.plus(PLACES_BUDGET));
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No places found for " + destination);
        }
        List<Place> places = shortlist(found, prefs == null ? Set.of() : prefs.interests(), days, perDay);
        Map<Integer, Place> byId = places.stream().collect(Collectors.toMap(Place::id, Function.identity()));
        String list = places.stream()
                .map(p -> p.id() + ". " + p.name() + " (" + p.category() + ")"
                        + (p.openingHours() != null ? " [open: " + p.openingHours() + "]" : ""))
                .collect(Collectors.joining("\n"));
        Instant placesDone = Instant.now();

        String interests = prefs == null ? "general sightseeing" : prefs.interests().toString();
        // Pace is an optional wizard step, so it may be empty.
        String pace = paces.isEmpty() ? "balanced" : paces.toString();
        String modes = prefs == null ? "solo" : prefs.travelModes().toString();

        String prompt = """
                You are a travel planner. Plan a %d-day trip to %s starting on %s (day 1 is a %s).
                Daily window: %s to %s. Travel mode: %s. Interests: %s. Pace: %s
                (relaxed = 3 stops/day, balanced = 4-5, packed = 6-7). Every day needs stops.
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

        // 2. Gemini picks and roughly schedules them.
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

        // 3. We decide the final order and times: walking between stops, opening hours, the pace.
        List<Picked> picked = validStops(aiStops, byId, days);
        List<PlannedStop> plan = planDays(picked, places, req.startDate(), days,
                minuteOfDay(req.dayStart()), minuteOfDay(req.dayEnd()), perDay);
        if (plan.isEmpty()) {
            log.warn("No usable stops in the model's answer: {}",
                    answer == null || answer.length() <= 2000 ? answer : answer.substring(0, 2000) + "...");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Plan generation returned no usable stops");
        }

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
        for (PlannedStop p : plan) {
            trip.addStop(TripStop.builder()
                    .dayNumber(p.day()).position(p.position())
                    .name(p.place().name()).category(p.place().category())
                    .latitude(p.place().lat()).longitude(p.place().lon())
                    .startTime(LocalTime.of(p.start() / 60, p.start() % 60))
                    .durationMinutes(p.minutes())
                    .description(shorten(p.description()))
                    .openingHours(p.place().openingHours())
                    .build());
        }
        updateCenter(trip);

        log.info("Plan for {} days: places {} ms ({} found, {} sent), gemini {} ms, {} stops from the model, {} planned",
                days, Duration.between(began, placesDone).toMillis(), found.size(), places.size(),
                Duration.between(placesDone, geminiDone).toMillis(), aiStops.size(), plan.size());

        Trip saved = tripRepository.save(trip);
        return toResponse(saved);
    }

    /** The pace picked for this trip wins over the ones saved in the preferences. */
    static Set<Pace> chosenPaces(Pace forThisTrip, Set<Pace> fromPreferences) {
        return forThisTrip != null ? Set.of(forThisTrip) : fromPreferences;
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
     * Stops that name a known place once, fall on a trip day and last a realistic time, sorted by day
     * and the model's start time. Times, opening hours and walking are checked later, by the planner.
     */
    static List<Picked> validStops(List<AiStop> stops, Map<Integer, Place> byId, int days) {
        List<Picked> candidates = new ArrayList<>();
        Map<String, Integer> dropped = new LinkedHashMap<>();
        for (AiStop s : stops) {
            Place p = null;
            String reason = null;
            if (s == null || s.id() == null || s.day() == null || s.durationMinutes() == null) {
                reason = "missing field";
            } else if ((p = byId.get(s.id())) == null) {
                reason = "unknown place";
            } else if (s.day() < 1 || s.day() > days) {
                reason = "outside trip days";
            } else if (s.durationMinutes() < MIN_STOP_MINUTES || s.durationMinutes() > MAX_STOP_MINUTES) {
                reason = "unrealistic duration";
            }
            if (reason != null) {
                dropped.merge(reason, 1, Integer::sum);
            } else {
                candidates.add(new Picked(s, p, parseTime(s.start())));
            }
        }
        candidates.sort(Comparator.comparingInt((Picked x) -> x.ai().day())
                .thenComparing(Picked::start, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Picked> kept = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (Picked x : candidates) {
            if (used.add(x.ai().id())) {
                kept.add(x);
            } else {
                dropped.merge("duplicate place", 1, Integer::sum);
            }
        }
        if (!dropped.isEmpty()) log.info("Dropped stops from the model: {}", dropped);
        return kept;
    }

    /**
     * The final plan. Each day keeps at most {@code perDay} of the model's stops (in its order) and the
     * planner orders and times them: walking time, opening hours, daily window. Stops that can't fit are
     * left out. Days that end up empty are filled with unused nearby places that are open that day.
     */
    static List<PlannedStop> planDays(List<Picked> picked, List<Place> pool, LocalDate startDate, int days,
                                      int windowStart, int windowEnd, int perDay) {
        Map<Integer, List<Picked>> byDay = new TreeMap<>();
        for (Picked p : picked) byDay.computeIfAbsent(p.ai().day(), d -> new ArrayList<>()).add(p);

        Set<String> usedNames = new HashSet<>();
        Map<Integer, List<Timed>> timedByDay = new TreeMap<>();
        Map<Integer, String> descriptions = new HashMap<>();
        int notFitting = 0;
        for (int day = 1; day <= days; day++) {
            List<Picked> dayStops = byDay.getOrDefault(day, List.of());
            List<DayPlanner.Stop> stops = new ArrayList<>();
            for (Picked p : dayStops.subList(0, Math.min(perDay, dayStops.size()))) {
                stops.add(toPlannerStop(p.place(), p.ai().durationMinutes()));
                descriptions.put(p.place().id(), p.ai().description());
            }
            List<Timed> timed = DayPlanner.planDropping(stops, startDate.plusDays(day - 1).getDayOfWeek(),
                    windowStart, windowEnd);
            notFitting += dayStops.size() - timed.size();
            timed.forEach(t -> usedNames.add(t.stop().name().toLowerCase(Locale.ROOT)));
            timedByDay.put(day, timed);
        }
        if (notFitting > 0) log.info("{} of the model's stops didn't fit their day (pace, hours, walking)", notFitting);

        for (int day = 1; day <= days; day++) {
            if (!timedByDay.get(day).isEmpty()) continue;
            List<Timed> filled = fillDay(pool, usedNames, startDate.plusDays(day - 1).getDayOfWeek(),
                    windowStart, windowEnd, Math.min(FILL_STOPS, perDay));
            filled.forEach(t -> usedNames.add(t.stop().name().toLowerCase(Locale.ROOT)));
            timedByDay.put(day, filled);
            if (filled.isEmpty()) log.warn("Day {} has no stops: nothing nearby fits", day);
            else log.info("Day {} was empty; filled with {} nearby places", day, filled.size());
        }

        Map<Integer, Place> placeById = pool.stream().collect(Collectors.toMap(Place::id, Function.identity()));
        List<PlannedStop> plan = new ArrayList<>();
        timedByDay.forEach((day, timed) -> {
            int position = 1;
            for (Timed t : timed) {
                Place place = placeById.get(t.stop().key());
                plan.add(new PlannedStop(day, position++, place, t.start(), t.stop().minutes(),
                        descriptions.get(place.id())));
            }
        });
        return plan;
    }

    /**
     * Up to {@code count} unused places for an empty day: the first sight that fits that day, then the
     * nearest others that still fit, and a Food place for lunch when the day spans midday.
     */
    static List<Timed> fillDay(List<Place> pool, Set<String> usedNames, DayOfWeek day,
                               int windowStart, int windowEnd, int count) {
        List<Place> free = pool.stream()
                .filter(p -> !usedNames.contains(p.name().toLowerCase(Locale.ROOT)))
                .toList();
        List<Place> sights = free.stream().filter(p -> !"Food".equals(p.category())).toList();
        List<DayPlanner.Stop> chosen = new ArrayList<>();
        List<Timed> plan = List.of();

        for (Place seed : sights) {
            DayPlanner.Stop stop = toPlannerStop(seed, null);
            Optional<List<Timed>> p = DayPlanner.plan(List.of(stop), day, windowStart, windowEnd);
            if (p.isPresent()) {
                chosen.add(stop);
                plan = p.get();
                break;
            }
        }
        while (!chosen.isEmpty() && chosen.size() < count) {
            DayPlanner.Stop last = chosen.get(chosen.size() - 1);
            Optional<List<Timed>> next = Optional.empty();
            DayPlanner.Stop added = null;
            for (Place c : nearest(sights, last.lat(), last.lon(), chosen, 10)) {
                List<DayPlanner.Stop> attempt = new ArrayList<>(chosen);
                attempt.add(toPlannerStop(c, null));
                next = DayPlanner.plan(attempt, day, windowStart, windowEnd);
                if (next.isPresent()) {
                    added = attempt.get(attempt.size() - 1);
                    break;
                }
            }
            if (added == null) break;
            chosen.add(added);
            plan = next.get();
        }
        if (!chosen.isEmpty() && windowStart <= 12 * 60 && windowEnd >= 14 * 60) {
            double lat = chosen.stream().mapToDouble(DayPlanner.Stop::lat).average().orElse(0);
            double lon = chosen.stream().mapToDouble(DayPlanner.Stop::lon).average().orElse(0);
            List<Place> food = free.stream().filter(p -> "Food".equals(p.category())).toList();
            for (Place f : nearest(food, lat, lon, chosen, 5)) {
                List<DayPlanner.Stop> attempt = new ArrayList<>(chosen);
                attempt.add(toPlannerStop(f, null));
                Optional<List<Timed>> withLunch = DayPlanner.plan(attempt, day, windowStart, windowEnd);
                if (withLunch.isPresent()) {
                    plan = withLunch.get();
                    break;
                }
            }
        }
        return plan;
    }

    private static List<Place> nearest(List<Place> places, double lat, double lon,
                                       List<DayPlanner.Stop> exclude, int limit) {
        Set<Integer> taken = exclude.stream().map(DayPlanner.Stop::key).collect(Collectors.toSet());
        return places.stream()
                .filter(p -> !taken.contains(p.id()))
                .sorted(Comparator.comparingDouble(p -> Geo.distanceMeters(lat, lon, p.lat(), p.lon())))
                .limit(limit)
                .toList();
    }

    private static DayPlanner.Stop toPlannerStop(Place p, Integer minutes) {
        return new DayPlanner.Stop(p.id(), p.name(), p.category(), p.lat(), p.lon(), p.openingHours(),
                minutes != null ? minutes : defaultMinutes(p.category()));
    }

    static int defaultMinutes(String category) {
        return category == null ? 60 : DEFAULT_MINUTES.getOrDefault(category, 60);
    }

    // ---------------------------------------------------------------- reading and editing a trip

    @Transactional(readOnly = true)
    public TripResponse getTrip(String email, UUID tripId) {
        return toResponse(load(email, tripId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TripSummaryResponse> savedTrips(String email, Pageable pageable) {
        return PageResponse.from(tripRepository.findByUserEmailAndSavedTrue(email, pageable),
                t -> new TripSummaryResponse(t.getPublicId(), t.getDestination(), t.getCountry(),
                        t.getStartDate(), t.getEndDate()));
    }

    /** Keeps the plan. Saving twice changes nothing. */
    @Transactional
    public TripResponse save(String email, UUID tripId) {
        Trip trip = load(email, tripId);
        trip.markSaved();
        return toResponse(trip);
    }

    /** Throws away a plan the traveler didn't want; a saved plan is refused with 409. */
    @Transactional
    public void deleteDraft(String email, UUID tripId) {
        Trip trip = load(email, tripId);
        if (trip.isSaved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A saved plan can't be discarded.");
        }
        tripRepository.delete(trip);
    }

    /** Removes a stop; the rest of that day is re-planned (order and times). */
    @Transactional
    public TripResponse removeStop(String email, UUID tripId, UUID stopId) {
        Trip trip = load(email, tripId);
        TripStop stop = findStop(trip, stopId);
        trip.removeStop(stop);
        // Fewer stops always fit; if the planner still found nothing, the old times simply stay.
        replanDay(trip, stop.getDayNumber());
        updateCenter(trip);
        return toResponse(trip);
    }

    /** Adds a place to a day and re-plans it; 422 when the day can't take it. */
    @Transactional
    public TripResponse addStop(String email, UUID tripId, StopRequest req) {
        Trip trip = load(email, tripId);
        int days = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        if (req.dayNumber() == null || req.dayNumber() > days) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a day between 1 and " + days + ".");
        }
        int day = req.dayNumber();
        if (trip.getStops().stream().filter(s -> s.getDayNumber() == day).count() >= MAX_STOPS_PER_DAY) {
            throw unprocessable("Day " + day + " already has " + MAX_STOPS_PER_DAY + " stops. Remove one first.");
        }
        rejectDuplicate(trip, req.name(), null);
        trip.addStop(newStop(day, req));
        replanDay(trip, day).orElseThrow(() -> unprocessable(
                req.name().trim() + " doesn't fit into day " + day + " (opening hours or time left in the day)."));
        updateCenter(trip);
        return toResponse(trip);
    }

    /** Puts another place in a stop's day instead of it, and re-plans the day; 422 when it can't fit. */
    @Transactional
    public TripResponse replaceStop(String email, UUID tripId, UUID stopId, StopRequest req) {
        Trip trip = load(email, tripId);
        TripStop old = findStop(trip, stopId);
        int day = old.getDayNumber();
        rejectDuplicate(trip, req.name(), old);
        trip.removeStop(old);
        trip.addStop(newStop(day, req));
        replanDay(trip, day).orElseThrow(() -> unprocessable(
                req.name().trim() + " doesn't fit into day " + day + " (opening hours or time left in the day)."));
        updateCenter(trip);
        return toResponse(trip);
    }

    /**
     * Real places near the trip that aren't in it yet, nearest first: near that day's stops when
     * {@code day} has some, otherwise near the trip's centre. Not @Transactional: the place lookup may
     * take seconds; the trip (with its stops) is read in one short query.
     */
    public List<CandidateResponse> candidates(String email, UUID tripId, Integer day) {
        Trip trip = load(email, tripId);
        Set<String> inTrip = trip.getStops().stream()
                .map(s -> s.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<TripStop> dayStops = day == null ? List.of()
                : trip.getStops().stream().filter(s -> s.getDayNumber().equals(day)).toList();
        double lat = dayStops.isEmpty() ? trip.getLatitude()
                : dayStops.stream().mapToDouble(TripStop::getLatitude).average().orElse(0);
        double lon = dayStops.isEmpty() ? trip.getLongitude()
                : dayStops.stream().mapToDouble(TripStop::getLongitude).average().orElse(0);

        List<Place> found = geocoder.candidates(trip.getDestination(),
                new Coordinates(trip.getLatitude(), trip.getLongitude()), Instant.now().plus(CANDIDATES_BUDGET));
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not load places near " + trip.getDestination() + " right now. Try again.");
        }
        return found.stream()
                .filter(p -> !inTrip.contains(p.name().toLowerCase(Locale.ROOT)))
                .map(p -> new CandidateResponse(p.name(), p.category(), p.lat(), p.lon(), p.openingHours(),
                        (int) Math.round(Geo.distanceMeters(lat, lon, p.lat(), p.lon()))))
                .sorted(Comparator.comparingInt(CandidateResponse::distanceMeters))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private Trip load(String email, UUID tripId) {
        return tripRepository.findByPublicIdAndUserEmail(tripId, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    private static TripStop findStop(Trip trip, UUID stopId) {
        return trip.getStops().stream().filter(s -> s.getPublicId().equals(stopId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found"));
    }

    private static void rejectDuplicate(Trip trip, String name, TripStop except) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        boolean taken = trip.getStops().stream()
                .anyMatch(s -> s != except && s.getName().toLowerCase(Locale.ROOT).equals(wanted));
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, name.trim() + " is already in your trip.");
        }
    }

    private static TripStop newStop(int day, StopRequest req) {
        String category = req.category() == null || req.category().isBlank() ? null : req.category().trim();
        String hours = req.openingHours() == null || req.openingHours().isBlank() ? null : req.openingHours().trim();
        return TripStop.builder()
                .dayNumber(day).position(Integer.MAX_VALUE)  // last in the current order; the re-plan renumbers
                .name(req.name().trim()).category(category)
                .latitude(req.latitude()).longitude(req.longitude())
                .startTime(LocalTime.MIDNIGHT)
                .durationMinutes(req.durationMinutes() != null ? req.durationMinutes() : defaultMinutes(category))
                .openingHours(hours)
                .build();
    }

    /**
     * Re-plans one day of a saved trip from its current order. Positions and times are rewritten in
     * place (the order constraint is only checked at commit, see V7). Empty when the day's stops can't
     * all fit; nothing is changed then.
     */
    static Optional<List<Timed>> replanDay(Trip trip, int day) {
        List<TripStop> stops = trip.getStops().stream()
                .filter(s -> s.getDayNumber() == day)
                .sorted(Comparator.comparingInt(TripStop::getPosition))
                .toList();
        List<DayPlanner.Stop> plannerStops = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            TripStop s = stops.get(i);
            plannerStops.add(new DayPlanner.Stop(i, s.getName(), s.getCategory(), s.getLatitude(),
                    s.getLongitude(), s.getOpeningHours(), s.getDurationMinutes()));
        }
        DayOfWeek weekday = trip.getStartDate().plusDays(day - 1).getDayOfWeek();
        Optional<List<Timed>> plan = DayPlanner.plan(plannerStops, weekday,
                minuteOfDay(trip.getDayStart()), minuteOfDay(trip.getDayEnd()));
        plan.ifPresent(timed -> {
            int position = 1;
            for (Timed t : timed) {
                TripStop s = stops.get(t.stop().key());
                s.setPosition(position++);
                s.setStartTime(LocalTime.of(t.start() / 60, t.start() % 60));
            }
        });
        return plan;
    }

    private static ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatusCode.valueOf(422), message);
    }

    private static void updateCenter(Trip trip) {
        if (trip.getStops().isEmpty()) return;
        trip.setCenter(
                trip.getStops().stream().mapToDouble(TripStop::getLatitude).average().orElse(0),
                trip.getStops().stream().mapToDouble(TripStop::getLongitude).average().orElse(0));
    }

    // ---------------------------------------------------------------- helpers

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

    private static String shorten(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        return s.length() > 300 ? s.substring(0, 297) + "..." : s;
    }

    private TripResponse toResponse(Trip t) {
        List<TripResponse.StopResponse> stops = t.getStops().stream()
                .sorted(Comparator.comparingInt(TripStop::getDayNumber).thenComparingInt(TripStop::getPosition))
                .map(s -> new TripResponse.StopResponse(
                        s.getPublicId(), s.getDayNumber(), s.getPosition(), s.getName(), s.getCategory(),
                        s.getLatitude(), s.getLongitude(), s.getStartTime(), s.getDurationMinutes(),
                        s.getDescription(), s.getOpeningHours()))
                .toList();
        return new TripResponse(t.getPublicId(), t.getDestination(), t.getCountry(),
                t.getLatitude(), t.getLongitude(), t.getStartDate(), t.getEndDate(),
                t.getDayStart(), t.getDayEnd(), t.isSaved(), stops);
    }
}

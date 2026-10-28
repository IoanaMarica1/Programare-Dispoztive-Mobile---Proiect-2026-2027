package com.wanderly.backend.service;

import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripStop;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.TripRepository;
import com.wanderly.backend.repository.UserRepository;
import com.wanderly.backend.service.GeocodingClient.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final PreferencesService preferencesService;
    private final GeminiClient gemini;
    private final GeocodingClient geocoder;
    private final tools.jackson.databind.json.JsonMapper json =
            tools.jackson.databind.json.JsonMapper.builder().build();

    public TripService(TripRepository tripRepository, UserRepository userRepository,
                       PreferencesService preferencesService, GeminiClient gemini,
                       GeocodingClient geocoder) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.preferencesService = preferencesService;
        this.gemini = gemini;
        this.geocoder = geocoder;
    }

    /** Gemini only picks places by id and schedules them. */
    record AiStop(int day, int id, String start, int durationMinutes) {}
    record AiPlan(List<AiStop> stops) {}
    private record Picked(AiStop ai, Place place, LocalTime start) {}

    @Transactional
    public TripResponse generate(String email, GenerateTripRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // 1. Real places for this city (cached after the first time).
        long t0 = System.currentTimeMillis();
        List<Place> places = geocoder.candidates(req.destination());
        if (places.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No places found for " + req.destination());
        }
        Map<Integer, Place> byId = places.stream().collect(Collectors.toMap(Place::id, Function.identity()));
        String list = places.stream()
                .map(p -> p.id() + ". " + p.name() + " (" + p.category() + ")")
                .collect(Collectors.joining("\n"));
        long t1 = System.currentTimeMillis();

        long days = ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
        var prefs = preferencesService.getPreferences(email).orElse(null);
        String interests = prefs == null ? "general sightseeing" : prefs.interests().toString();
        String pace = prefs == null ? "balanced" : prefs.paces().toString();
        String modes = prefs == null ? "solo" : prefs.travelModes().toString();

        String prompt = """
                You are a travel planner. Plan a %d-day trip to %s.
                Daily window: %s to %s. Travel mode: %s. Interests: %s. Pace: %s
                (relaxed = 3 stops/day, balanced = 4-5, packed = 6-7).
                Choose ONLY from these real places (use their id):
                %s
                Rules:
                - Prefer places matching the interests; include at most one Food stop per meal time.
                - Group nearby places on the same day.
                - Every stop starts and ends inside the daily window, when the place is normally open.
                - Never use the same id twice.
                Return ONLY JSON: {"stops":[{"day":1,"id":12,"start":"HH:mm","durationMinutes":60}]}
                """.formatted(days, req.destination(), req.dayStart(), req.dayEnd(),
                modes, interests, pace, list);

        // 2. Gemini schedules them.
        AiPlan plan;
        try {
            plan = json.readValue(gemini.generateJson(prompt), AiPlan.class);
        } catch (RuntimeException e) {
            log.error("Plan generation failed", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Plan generation failed", e);
        }
        long t2 = System.currentTimeMillis();

        // 3. Validate: known id, used once, inside the day window; then order by day and time.
        Set<Integer> used = new HashSet<>();
        List<Picked> picked = new ArrayList<>();
        for (AiStop s : plan.stops() == null ? List.<AiStop>of() : plan.stops()) {
            Place p = byId.get(s.id());
            if (p == null || s.day() < 1 || s.day() > days || s.durationMinutes() <= 0 || !used.add(s.id())) continue;
            LocalTime start;
            try { start = LocalTime.parse(s.start()); } catch (Exception e) { continue; }
            if (start.isBefore(req.dayStart())
                    || start.plusMinutes(s.durationMinutes()).isAfter(req.dayEnd())) continue;
            picked.add(new Picked(s, p, start));
        }
        picked.sort(Comparator.comparingInt((Picked x) -> x.ai().day()).thenComparing(Picked::start));

        Trip trip = Trip.builder()
                .user(user)
                .destination(req.destination())
                .country(req.country())
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
                    x.place().lat(), x.place().lon(), x.start(), x.ai().durationMinutes()));
        }
        if (trip.getStops().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Plan generation returned no usable stops");
        }
        trip.setCenter(
                trip.getStops().stream().mapToDouble(TripStop::getLatitude).average().orElse(0),
                trip.getStops().stream().mapToDouble(TripStop::getLongitude).average().orElse(0));

        log.info("Plan for {} days: places {} ms ({} candidates), gemini {} ms, {} stops",
                days, t1 - t0, places.size(), t2 - t1, trip.getStops().size());

        Trip saved = tripRepository.save(trip);
        return toResponse(saved);
    }

    private TripStop stop(int day, int pos, String name, String category,
                          double lat, double lon, LocalTime start, int minutes) {
        return TripStop.builder()
                .dayNumber(day).position(pos).name(name).category(category)
                .latitude(lat).longitude(lon).startTime(start).durationMinutes(minutes)
                .build();
    }

    private TripResponse toResponse(Trip t) {
        List<TripResponse.StopResponse> stops = t.getStops().stream()
                .map(s -> new TripResponse.StopResponse(
                        s.getDayNumber(), s.getPosition(), s.getName(), s.getCategory(),
                        s.getLatitude(), s.getLongitude(), s.getStartTime(), s.getDurationMinutes()))
                .toList();
        return new TripResponse(t.getPublicId(), t.getDestination(), t.getCountry(),
                t.getLatitude(), t.getLongitude(), t.getStartDate(), t.getEndDate(),
                t.getDayStart(), t.getDayEnd(), stops);
    }
}
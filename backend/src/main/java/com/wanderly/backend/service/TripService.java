package com.wanderly.backend.service;

import com.wanderly.backend.repository.UserRepository;
import com.wanderly.backend.model.User;
import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripStop;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final PreferencesService preferencesService;
    private final GeminiClient gemini;
    private final tools.jackson.databind.json.JsonMapper json =
            tools.jackson.databind.json.JsonMapper.builder().build();

    public TripService(TripRepository tripRepository, UserRepository userRepository,
                       PreferencesService preferencesService, GeminiClient gemini) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.preferencesService = preferencesService;
        this.gemini = gemini;
    }

    /** Shape we ask Gemini to return. */
    record AiStop(int day, int position, String name, String category,
                  double lat, double lon, String start, int durationMinutes) {}
    record AiPlan(List<AiStop> stops) {}

    @Transactional
    public TripResponse generate(String email, GenerateTripRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Trip trip = Trip.builder()
                .user(user)
                .destination(req.destination())
                .country(req.country())
                .latitude(req.latitude() != null ? req.latitude() : 41.8902)
                .longitude(req.longitude() != null ? req.longitude() : 12.4922)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .dayStart(req.dayStart())
                .dayEnd(req.dayEnd())
                .build();

        long days = java.time.temporal.ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
        var prefs = preferencesService.getPreferences(email).orElse(null);
        String interests = prefs == null ? "general sightseeing" : prefs.interests().toString();
        String pace = prefs == null ? "balanced" : prefs.paces().toString();
        String modes = prefs == null ? "solo" : prefs.travelModes().toString();

        String prompt = """
                You are a travel planner. Plan a %d-day trip to %s.
                Daily window: %s to %s. Travel mode: %s. Interests: %s. Pace: %s
                (relaxed = 3 stops/day, balanced = 4-5, packed = 6-7).
                Rules:
                - Only real, existing places in %s with accurate latitude/longitude.
                - Order stops each day so walking between them is short.
                - Every stop starts and ends inside the daily window, and only when the place is normally open.
                - No place appears twice in the whole trip.
                Return ONLY JSON of this exact shape:
                {"stops":[{"day":1,"position":1,"name":"...","category":"Historical|Museum|Food|Nature|Art|Landmark|Nightlife",
                "lat":0.0,"lon":0.0,"start":"HH:mm","durationMinutes":60}]}
                """.formatted(days, req.destination(), req.dayStart(), req.dayEnd(),
                modes, interests, pace, req.destination());

        AiPlan plan;
        try {
            plan = json.readValue(gemini.generateJson(prompt), AiPlan.class);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(TripService.class).error("Plan generation failed", e);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Plan generation failed", e);
        }

        // Keep only sane stops: valid day, inside the window, real coordinates, no duplicates.
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Map<Integer, Integer> nextPos = new java.util.HashMap<>();
        for (AiStop s : plan.stops() == null ? List.<AiStop>of() : plan.stops()) {
            if (s.day() < 1 || s.day() > days || s.name() == null || s.durationMinutes() <= 0) continue;
            if (Math.abs(s.lat()) > 90 || Math.abs(s.lon()) > 180 || !seen.add(s.name().toLowerCase())) continue;
            LocalTime start;
            try { start = LocalTime.parse(s.start()); } catch (Exception e) { continue; }
            if (start.isBefore(req.dayStart())
                    || start.plusMinutes(s.durationMinutes()).isAfter(req.dayEnd())) continue;
            int pos = nextPos.merge(s.day(), 1, Integer::sum);
            trip.addStop(stop(s.day(), pos, s.name(), s.category(),
                    s.lat(), s.lon(), start, s.durationMinutes()));
        }
        if (trip.getStops().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Plan generation returned no usable stops");
        }

        if (req.latitude() == null || req.longitude() == null) {
            trip.setCenter(
                    trip.getStops().stream().mapToDouble(TripStop::getLatitude).average().orElse(0),
                    trip.getStops().stream().mapToDouble(TripStop::getLongitude).average().orElse(0));
        }

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
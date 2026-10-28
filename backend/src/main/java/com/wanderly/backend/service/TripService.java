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

    public TripService(TripRepository tripRepository, UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }

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

        // Placeholder pool of Rome stops: name, category, lat, lon
        Object[][] pool = {
                {"Colosseum", "Historical", 41.8902, 12.4922},
                {"Roman Forum", "Ruins", 41.8925, 12.4853},
                {"Piazza Venezia", "Landmark", 41.8960, 12.4823},
                {"Pantheon", "Historical", 41.8986, 12.4769},
                {"Piazza Navona", "Landmark", 41.8992, 12.4731},
                {"Trevi Fountain", "Landmark", 41.9009, 12.4833},
                {"Spanish Steps", "Landmark", 41.9060, 12.4828},
                {"Trastevere", "Food", 41.8896, 12.4698},
                {"Vatican Museums", "Art", 41.9065, 12.4536},
                {"Castel Sant'Angelo", "Historical", 41.9031, 12.4663}
        };

        long days = java.time.temporal.ChronoUnit.DAYS
                .between(req.startDate(), req.endDate()) + 1;
        int stopsPerDay = 5;
        for (int d = 0; d < days; d++) {
            for (int p = 0; p < stopsPerDay; p++) {
                Object[] s = pool[(d * stopsPerDay + p) % pool.length];
                LocalTime start = LocalTime.of(9, 0).plusMinutes(p * 90L);
                trip.addStop(stop(d + 1, p + 1, (String) s[0], (String) s[1],
                        (double) s[2], (double) s[3], start, 60));
            }
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
package com.wanderly.backend.service;

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

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Transactional
    public TripResponse generate(User user, GenerateTripRequest req) {
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

        // Hardcoded placeholder plan (Rome), replaced by the AI in B3.
        trip.addStop(stop(1, 1, "Colosseum", "Historical", 41.8902, 12.4922, LocalTime.of(9, 30), 90));
        trip.addStop(stop(1, 2, "Roman Forum", "Ruins", 41.8925, 12.4853, LocalTime.of(11, 15), 55));
        trip.addStop(stop(1, 3, "Piazza Venezia", "Landmark", 41.8960, 12.4823, LocalTime.of(12, 30), 30));
        trip.addStop(stop(1, 4, "Pantheon", "Historical", 41.8986, 12.4769, LocalTime.of(14, 0), 45));

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
package com.wanderly.backend.service;

import com.wanderly.backend.dto.common.PageResponse;
import com.wanderly.backend.dto.history.CountryHistoryResponse;
import com.wanderly.backend.dto.visit.VisitRequest;
import com.wanderly.backend.dto.visit.VisitResponse;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.model.User;
import com.wanderly.backend.model.Visit;
import com.wanderly.backend.repository.VisitRepository;
import com.wanderly.backend.service.GeocodingClient.Coordinates;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

@Service
public class VisitService {

    public static final int FIRST_VISIT_POINTS = 10;
    /** A check-in only counts within this distance of the objective (brief: anti-abuse). */
    public static final int MAX_CHECK_IN_METERS = 150;
    private static final Duration LOOKUP_BUDGET = Duration.ofSeconds(10);

    private final VisitRepository visitRepository;
    private final UserService userService;
    private final GeocodingClient geocoder;
    private final TransactionOperations transaction;

    public VisitService(VisitRepository visitRepository, UserService userService,
                        GeocodingClient geocoder, TransactionOperations transaction) {
        this.visitRepository = visitRepository;
        this.userService = userService;
        this.geocoder = geocoder;
        this.transaction = transaction;
    }

    /**
     * Checks the traveler in at an objective. Points are awarded only the first time, and only when
     * the traveler's position (from the request) is within 150 m of where OpenStreetMap places the
     * objective. The map lookup runs before the transaction, so it doesn't hold a database connection.
     */
    public VisitResultResponse recordVisit(String email, VisitRequest request) {
        String objectiveName = request.objectiveName().trim();
        String city = request.city().trim();
        String country = request.country().trim();

        User user = userService.getByEmail(email);
        Optional<Visit> existing = findVisit(user, objectiveName, city, country);
        if (existing.isPresent()) {
            return new VisitResultResponse(toResponse(existing.get()), false, 0, user.getPoints());
        }

        Coordinates objective = locate(objectiveName, city, country);
        long meters = Math.round(distanceMeters(request.latitude(), request.longitude(), objective.lat(), objective.lon()));
        if (meters > MAX_CHECK_IN_METERS) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "You are " + meters + " m from "
                    + objectiveName + ". Check in when you are within " + MAX_CHECK_IN_METERS + " m.");
        }

        return transaction.execute(status -> {
            // Locks the traveler's row: concurrent check-ins queue here, so none of them loses points,
            // and the second of two identical ones sees the first and awards nothing.
            User locked = userService.getByEmailForUpdate(email);
            Optional<Visit> again = findVisit(locked, objectiveName, city, country);
            if (again.isPresent()) {
                return new VisitResultResponse(toResponse(again.get()), false, 0, locked.getPoints());
            }

            // Reuse the spelling already saved for this country, so history groups it as one.
            String savedCountry = visitRepository.findFirstByUserIdAndCountryIgnoreCase(locked.getId(), country)
                    .map(Visit::getCountry)
                    .orElse(country);

            Visit visit = visitRepository.save(Visit.builder()
                    .user(locked)
                    .objectiveName(objectiveName)
                    .city(city)
                    .country(savedCountry)
                    .latitude(objective.lat())
                    .longitude(objective.lon())
                    .visitedAt(Instant.now())
                    .build());

            locked.setPoints(locked.getPoints() + FIRST_VISIT_POINTS);
            return new VisitResultResponse(toResponse(visit), true, FIRST_VISIT_POINTS, locked.getPoints());
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<CountryHistoryResponse> getVisitedCountries(String email, Pageable pageable) {
        User user = userService.getByEmail(email);
        return PageResponse.from(visitRepository.findCountriesByUserId(user.getId(), pageable), Function.identity());
    }

    @Transactional(readOnly = true)
    public PageResponse<VisitResponse> getVisitsInCountry(String email, String country, Pageable pageable) {
        User user = userService.getByEmail(email);
        return PageResponse.from(
                visitRepository.findByUserIdAndCountryIgnoreCaseOrderByVisitedAtDesc(user.getId(), country.trim(), pageable),
                this::toResponse);
    }

    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        return Geo.distanceMeters(lat1, lon1, lat2, lon2);
    }

    private Optional<Visit> findVisit(User user, String objectiveName, String city, String country) {
        return visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                user.getId(), objectiveName, city, country);
    }

    private Coordinates locate(String objectiveName, String city, String country) {
        try {
            return geocoder.locate(objectiveName + ", " + city + ", " + country, Instant.now().plus(LOOKUP_BUDGET))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Could not find " + objectiveName + " in " + city + " on the map."));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not check your location right now. Try again.");
        }
    }

    private VisitResponse toResponse(Visit visit) {
        return new VisitResponse(
                visit.getPublicId(),
                visit.getObjectiveName(),
                visit.getCity(),
                visit.getCountry(),
                visit.getLatitude(),
                visit.getLongitude(),
                visit.getVisitedAt()
        );
    }
}

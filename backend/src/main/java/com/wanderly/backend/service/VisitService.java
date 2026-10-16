package com.wanderly.backend.service;

import com.wanderly.backend.dto.common.PageResponse;
import com.wanderly.backend.dto.history.CountryHistoryResponse;
import com.wanderly.backend.dto.visit.VisitRequest;
import com.wanderly.backend.dto.visit.VisitResponse;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.model.User;
import com.wanderly.backend.model.Visit;
import com.wanderly.backend.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class VisitService {

    public static final int FIRST_VISIT_POINTS = 10;

    private final VisitRepository visitRepository;
    private final UserService userService;

    // Points are awarded only the first time a traveler visits an objective.
    @Transactional
    public VisitResultResponse recordVisit(String email, VisitRequest request) {
        User user = userService.getByEmail(email);
        String objectiveName = request.objectiveName().trim();
        String city = request.city().trim();
        String country = request.country().trim();

        Optional<Visit> existing = visitRepository
                .findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                        user.getId(), objectiveName, city, country);
        if (existing.isPresent()) {
            return new VisitResultResponse(toResponse(existing.get()), false, 0, user.getPoints());
        }

        Visit visit = visitRepository.save(Visit.builder()
                .user(user)
                .objectiveName(objectiveName)
                .city(city)
                .country(country)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .visitedAt(Instant.now())
                .build());

        user.setPoints(user.getPoints() + FIRST_VISIT_POINTS);
        return new VisitResultResponse(toResponse(visit), true, FIRST_VISIT_POINTS, user.getPoints());
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

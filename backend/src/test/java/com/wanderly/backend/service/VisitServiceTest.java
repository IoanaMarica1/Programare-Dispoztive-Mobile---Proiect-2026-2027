package com.wanderly.backend.service;

import com.wanderly.backend.dto.visit.VisitRequest;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.model.User;
import com.wanderly.backend.model.Visit;
import com.wanderly.backend.repository.UserRepository;
import com.wanderly.backend.repository.VisitRepository;
import com.wanderly.backend.service.GeocodingClient.Coordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    // Bran Castle on OpenStreetMap, and a point ~100 m north of it.
    private static final Coordinates BRAN = new Coordinates(45.5150, 25.3672);
    private static final double NEAR_LAT = 45.5159;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeocodingClient geocoder;

    private VisitService visitService;
    private User user;

    @BeforeEach
    void setUp() {
        visitService = new VisitService(visitRepository, new UserService(userRepository), geocoder,
                TransactionOperations.withoutTransaction());
        user = User.builder().email("ana@test.com").name("Ana").address("Cluj").age(26).build();
        user.setPoints(30);
        ReflectionTestUtils.setField(user, "id", 1L);
        lenient().when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
    }

    private Visit existingVisit() {
        Visit existing = Visit.builder()
                .user(user).objectiveName("Castelul Bran").city("Bran").country("Romania")
                .latitude(BRAN.lat()).longitude(BRAN.lon()).visitedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .build();
        ReflectionTestUtils.setField(existing, "id", 7L);
        return existing;
    }

    @Test
    @DisplayName("Prima vizită, la ~100 m de obiectiv → se salvează cu coordonatele din OSM și primește +10 puncte")
    void recordVisit_firstVisitNearby_awardsTenPoints() {
        when(visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                1L, "Castelul Bran", "Bran", "Romania")).thenReturn(Optional.empty());
        when(geocoder.locate(eq("Castelul Bran, Bran, Romania"), any())).thenReturn(Optional.of(BRAN));
        when(userRepository.findByEmailForUpdate("ana@test.com")).thenReturn(Optional.of(user));
        when(visitRepository.save(any(Visit.class))).thenAnswer(i -> i.getArgument(0));

        VisitResultResponse result = visitService.recordVisit("ana@test.com",
                new VisitRequest(" Castelul Bran ", "Bran", "Romania", NEAR_LAT, BRAN.lon()));

        assertThat(result.firstVisit()).isTrue();
        assertThat(result.pointsAwarded()).isEqualTo(10);
        assertThat(result.totalPoints()).isEqualTo(40);
        assertThat(user.getPoints()).isEqualTo(40);
        assertThat(result.visit().objectiveName()).isEqualTo("Castelul Bran");
        assertThat(result.visit().latitude()).isEqualTo(BRAN.lat());
        assertThat(result.visit().visitedAt()).isNotNull();
    }

    @Test
    @DisplayName("Check-in de la ~1 km de obiectiv → 422, fără puncte și fără vizită salvată")
    void recordVisit_tooFar_isRejected() {
        when(visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                1L, "Castelul Bran", "Bran", "Romania")).thenReturn(Optional.empty());
        when(geocoder.locate(anyString(), any())).thenReturn(Optional.of(BRAN));

        assertThatThrownBy(() -> visitService.recordVisit("ana@test.com",
                new VisitRequest("Castelul Bran", "Bran", "Romania", BRAN.lat() + 0.009, BRAN.lon())))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(422);
                    assertThat(e.getReason()).contains("150 m");
                });
        assertThat(user.getPoints()).isEqualTo(30);
        verify(visitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Obiectiv pe care harta nu-l cunoaște → 404; Nominatim indisponibil → 503")
    void recordVisit_unknownOrUnavailable() {
        when(visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(geocoder.locate(eq("Nowhere, Bran, Romania"), any())).thenReturn(Optional.empty());
        when(geocoder.locate(eq("Castelul Bran, Bran, Romania"), any()))
                .thenThrow(new IllegalStateException("Place lookup unavailable"));

        assertThatThrownBy(() -> visitService.recordVisit("ana@test.com",
                new VisitRequest("Nowhere", "Bran", "Romania", NEAR_LAT, BRAN.lon())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> visitService.recordVisit("ana@test.com",
                new VisitRequest("Castelul Bran", "Bran", "Romania", NEAR_LAT, BRAN.lon())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    @DisplayName("Vizită repetată la același obiectiv → 0 puncte, fără căutare pe hartă și fără salvare")
    void recordVisit_duplicate_awardsNoPoints() {
        when(visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                1L, "castelul bran", "Bran", "Romania")).thenReturn(Optional.of(existingVisit()));

        VisitResultResponse result = visitService.recordVisit("ana@test.com",
                new VisitRequest("castelul bran", "Bran", "Romania", 0.0, 0.0));

        assertThat(result.firstVisit()).isFalse();
        assertThat(result.pointsAwarded()).isZero();
        assertThat(result.totalPoints()).isEqualTo(30);
        assertThat(result.visit().visitedAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        verify(geocoder, never()).locate(any(), any());
        verify(visitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Două check-in-uri simultane: al doilea vede vizita primului după lock și nu mai dă puncte")
    void recordVisit_concurrentDuplicate_awardsOnce() {
        when(visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                1L, "Castelul Bran", "Bran", "Romania"))
                .thenReturn(Optional.empty())                  // before the lock: not visited yet
                .thenReturn(Optional.of(existingVisit()));     // after it: the other request saved it
        when(geocoder.locate(anyString(), any())).thenReturn(Optional.of(BRAN));
        when(userRepository.findByEmailForUpdate("ana@test.com")).thenReturn(Optional.of(user));

        VisitResultResponse result = visitService.recordVisit("ana@test.com",
                new VisitRequest("Castelul Bran", "Bran", "Romania", NEAR_LAT, BRAN.lon()));

        assertThat(result.firstVisit()).isFalse();
        assertThat(user.getPoints()).isEqualTo(30);
        verify(visitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Țara scrisă altfel (\"romania\") se salvează cu denumirea existentă, ca istoricul s-o grupeze o dată")
    void recordVisit_reusesSavedCountrySpelling() {
        when(geocoder.locate(anyString(), any())).thenReturn(Optional.of(BRAN));
        when(userRepository.findByEmailForUpdate("ana@test.com")).thenReturn(Optional.of(user));
        when(visitRepository.findFirstByUserIdAndCountryIgnoreCase(1L, "romania"))
                .thenReturn(Optional.of(existingVisit()));
        when(visitRepository.save(any(Visit.class))).thenAnswer(i -> i.getArgument(0));

        VisitResultResponse result = visitService.recordVisit("ana@test.com",
                new VisitRequest("Castelul Bran", "Bran", "romania", NEAR_LAT, BRAN.lon()));

        assertThat(result.visit().country()).isEqualTo("Romania");
    }

    @Test
    @DisplayName("Distanța haversine: 0.001° latitudine ≈ 111 m")
    void distanceMeters() {
        assertThat(VisitService.distanceMeters(45.0, 25.0, 45.001, 25.0)).isBetween(110.0, 112.0);
        assertThat(VisitService.distanceMeters(45.0, 25.0, 45.0, 25.0)).isZero();
    }
}

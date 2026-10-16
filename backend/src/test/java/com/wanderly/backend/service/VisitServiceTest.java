package com.wanderly.backend.service;

import com.wanderly.backend.dto.visit.VisitRequest;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.model.User;
import com.wanderly.backend.model.Visit;
import com.wanderly.backend.repository.UserRepository;
import com.wanderly.backend.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private UserRepository userRepository;

    private VisitService visitService;
    private User user;

    @BeforeEach
    void setUp() {
        visitService = new VisitService(visitRepository, new UserService(userRepository));
        user = User.builder().email("ana@test.com").name("Ana").address("Cluj").age(26).build();
        user.setPoints(30);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Prima vizită la un obiectiv → se salvează și primește +10 puncte")
    void recordVisit_firstVisit_awardsTenPoints() {
        when(visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                1L, "Castelul Bran", "Bran", "Romania")).thenReturn(Optional.empty());
        when(visitRepository.save(any(Visit.class))).thenAnswer(i -> {
            Visit v = i.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 7L);
            return v;
        });

        VisitResultResponse result = visitService.recordVisit("ana@test.com",
                new VisitRequest(" Castelul Bran ", "Bran", "Romania", 45.515, 25.367));

        assertThat(result.firstVisit()).isTrue();
        assertThat(result.pointsAwarded()).isEqualTo(10);
        assertThat(result.totalPoints()).isEqualTo(40);
        assertThat(user.getPoints()).isEqualTo(40);
        assertThat(result.visit().publicId()).isNotNull();
        assertThat(result.visit().objectiveName()).isEqualTo("Castelul Bran");
        assertThat(result.visit().visitedAt()).isNotNull();
    }

    @Test
    @DisplayName("Vizită repetată la același obiectiv → 0 puncte, nu se salvează nimic nou")
    void recordVisit_duplicate_awardsNoPoints() {
        Visit existing = Visit.builder()
                .user(user).objectiveName("Castelul Bran").city("Bran").country("Romania")
                .latitude(45.515).longitude(25.367).visitedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .build();
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(visitRepository.findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
                1L, "castelul bran", "Bran", "Romania")).thenReturn(Optional.of(existing));

        VisitResultResponse result = visitService.recordVisit("ana@test.com",
                new VisitRequest("castelul bran", "Bran", "Romania", 45.515, 25.367));

        assertThat(result.firstVisit()).isFalse();
        assertThat(result.pointsAwarded()).isZero();
        assertThat(result.totalPoints()).isEqualTo(30);
        assertThat(user.getPoints()).isEqualTo(30);
        assertThat(result.visit().visitedAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        verify(visitRepository, never()).save(any());
    }
}

package com.wanderly.backend.service;

import com.wanderly.backend.dto.preferences.PreferencesRequest;
import com.wanderly.backend.dto.preferences.PreferencesResponse;
import com.wanderly.backend.model.*;
import com.wanderly.backend.repository.PreferencesRepository;
import com.wanderly.backend.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferencesServiceTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private PreferencesRepository preferencesRepository;

    @Mock
    private UserRepository userRepository;

    private PreferencesService preferencesService;
    private User user;

    @BeforeEach
    void setUp() {
        preferencesService = new PreferencesService(preferencesRepository, new UserService(userRepository));
        user = User.builder().email("ana@test.com").name("Ana").address("Cluj").age(26).build();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("Get: fără preferințe salvate → Optional gol")
    void getPreferences_none_returnsEmpty() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(preferencesService.getPreferences("ana@test.com")).isEmpty();
    }

    @Test
    @DisplayName("Save: prima dată creează preferințele legate de user")
    void savePreferences_firstTime_createsForUser() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(preferencesRepository.save(any(Preferences.class))).thenAnswer(i -> i.getArgument(0));

        PreferencesResponse response = preferencesService.savePreferences("ana@test.com", new PreferencesRequest(
                TravelMode.SOLO, Set.of(Interest.HISTORY, Interest.FOOD), Priority.CULTURE, Pace.BALANCED));

        ArgumentCaptor<Preferences> saved = ArgumentCaptor.forClass(Preferences.class);
        verify(preferencesRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(response.travelMode()).isEqualTo(TravelMode.SOLO);
        assertThat(response.interests()).containsExactlyInAnyOrder(Interest.HISTORY, Interest.FOOD);
        assertThat(response.priority()).isEqualTo(Priority.CULTURE);
        assertThat(response.pace()).isEqualTo(Pace.BALANCED);
    }

    @Test
    @DisplayName("Save: preferințe existente → se actualizează același rând, pașii săriți devin null")
    void savePreferences_existing_updatesSameRow() {
        Preferences existing = Preferences.builder()
                .user(user).travelMode(TravelMode.FAMILY)
                .interests(new HashSet<>(Set.of(Interest.NATURE)))
                .priority(Priority.BUDGET).pace(Pace.PACKED)
                .build();
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(preferencesRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(preferencesRepository.save(any(Preferences.class))).thenAnswer(i -> i.getArgument(0));

        PreferencesResponse response = preferencesService.savePreferences("ana@test.com",
                new PreferencesRequest(TravelMode.FRIENDS, Set.of(Interest.NIGHTLIFE), null, null));

        verify(preferencesRepository).save(existing);
        assertThat(existing.getId()).isEqualTo(5L);
        assertThat(response.travelMode()).isEqualTo(TravelMode.FRIENDS);
        assertThat(response.interests()).containsExactly(Interest.NIGHTLIFE);
        assertThat(response.priority()).isNull();
        assertThat(response.pace()).isNull();
    }

    @Test
    @DisplayName("Validare: travel mode lipsă și fără interese → respins")
    void request_missingRequiredSteps_failsValidation() {
        Set<ConstraintViolation<PreferencesRequest>> violations =
                VALIDATOR.validate(new PreferencesRequest(null, Set.of(), Priority.BUDGET, Pace.RELAXED));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("travelMode", "interests");
    }

    @Test
    @DisplayName("Validare: priority și pace sunt opționale")
    void request_optionalStepsSkipped_passesValidation() {
        assertThat(VALIDATOR.validate(
                new PreferencesRequest(TravelMode.COUPLE, Set.of(Interest.ART), null, null))).isEmpty();
    }
}

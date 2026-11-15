package com.wanderly.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.repository.TripRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TripSaveTest {

    private static final String EMAIL = "ana@example.com";

    private final TripRepository repository = mock(TripRepository.class);
    private final TripService service = new TripService(repository, null, null, null, null);
    private Trip trip;

    @BeforeEach
    void setUp() {
        trip = Trip.builder()
                .destination("Paris").latitude(48.85).longitude(2.35)
                .startDate(LocalDate.of(2026, 11, 12)).endDate(LocalDate.of(2026, 11, 14))
                .dayStart(LocalTime.of(9, 0)).dayEnd(LocalTime.of(18, 0))
                .build();
        when(repository.findByPublicIdAndUserEmail(trip.getPublicId(), EMAIL)).thenReturn(Optional.of(trip));
    }

    @Test
    void aGeneratedPlanIsADraft() {
        assertFalse(trip.isSaved());
    }

    @Test
    void savingMarksThePlanAndTellsTheApp() {
        assertTrue(service.save(EMAIL, trip.getPublicId()).saved());
        assertTrue(trip.isSaved());
    }

    @Test
    void aDraftCanBeDiscarded() {
        service.deleteDraft(EMAIL, trip.getPublicId());

        verify(repository).delete(trip);
    }

    @Test
    void aSavedPlanCannotBeDiscarded() {
        trip.markSaved();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.deleteDraft(EMAIL, trip.getPublicId()));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(repository, never()).delete(any(Trip.class));
    }

    @Test
    void anotherTravelersPlanLooksMissing() {
        UUID otherId = UUID.randomUUID();
        when(repository.findByPublicIdAndUserEmail(otherId, EMAIL)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.save(EMAIL, otherId));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void thePaceForThisTripWinsOverThePreferences() {
        assertEquals(Set.of(Pace.RELAXED), TripService.chosenPaces(Pace.RELAXED, Set.of(Pace.PACKED)));
    }

    @Test
    void withoutAPaceForThisTripThePreferencesApply() {
        assertEquals(Set.of(Pace.PACKED), TripService.chosenPaces(null, Set.of(Pace.PACKED)));
    }
}

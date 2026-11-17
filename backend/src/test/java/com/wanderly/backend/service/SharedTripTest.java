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

import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripMember;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.TripMemberRepository;
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

/** Group members can read a saved group trip and leave it; nothing else. */
class SharedTripTest {

    private static final String ORGANISER = "ana@example.com";
    private static final String FRIEND = "bogdan@example.com";
    private static final String STRANGER = "eve@example.com";

    private final TripRepository trips = mock(TripRepository.class);
    private final TripMemberRepository members = mock(TripMemberRepository.class);
    private final TripService tripService = new TripService(trips, null, null, null, null);
    private final GroupTripService groupService =
            new GroupTripService(tripService, null, null, null, members, trips);

    private Trip trip;
    private TripMember friendMembership;
    private UUID id;

    @BeforeEach
    void setUp() {
        User ana = User.builder().email(ORGANISER).name("Ana").address("Cluj").age(25).build();
        User bogdan = User.builder().email(FRIEND).name("Bogdan").address("Cluj").age(26).build();
        trip = Trip.builder()
                .user(ana).destination("Rome").latitude(41.9).longitude(12.5)
                .startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 2))
                .dayStart(LocalTime.of(9, 0)).dayEnd(LocalTime.of(18, 0))
                .build();
        trip.addMember(TripMember.builder().user(ana).name("Ana").position(1).interests(Set.of(Interest.ART)).build());
        friendMembership = TripMember.builder().user(bogdan).name("Bogdan").position(2)
                .interests(Set.of(Interest.FOOD)).build();
        trip.addMember(friendMembership);
        trip.markSaved();
        id = trip.getPublicId();

        // What the repositories return for each person (their queries enforce saved / not hidden).
        when(trips.findByPublicIdAndUserEmail(id, ORGANISER)).thenReturn(Optional.of(trip));
        when(trips.findByPublicIdAndUserEmail(id, FRIEND)).thenReturn(Optional.empty());
        when(trips.findByPublicIdAndUserEmail(id, STRANGER)).thenReturn(Optional.empty());
        when(trips.findSavedForMember(id, FRIEND)).thenReturn(Optional.of(trip));
        when(trips.findSavedForMember(id, STRANGER)).thenReturn(Optional.empty());
        when(trips.existsByPublicIdAndUserEmail(id, ORGANISER)).thenReturn(true);
        when(members.findMembership(id, FRIEND)).thenReturn(Optional.of(friendMembership));
    }

    @Test
    void organiserOwnsTheTrip() {
        TripResponse r = tripService.getTrip(ORGANISER, id);

        assertTrue(r.owner());
        assertEquals("Ana", r.organiserName());
    }

    @Test
    void memberSeesItReadOnly() {
        TripResponse r = tripService.getTrip(FRIEND, id);

        assertFalse(r.owner());
        assertEquals("Ana", r.organiserName());
        assertEquals(2, r.members().size());
    }

    @Test
    void strangerGetsNotFound() {
        assertNotFound(() -> tripService.getTrip(STRANGER, id));
    }

    @Test
    void memberCannotChangeAnything() {
        assertNotFound(() -> tripService.save(FRIEND, id));
        assertNotFound(() -> tripService.deleteDraft(FRIEND, id));
        assertNotFound(() -> tripService.removeStop(FRIEND, id, UUID.randomUUID()));
        assertNotFound(() -> groupService.regenerate(FRIEND, id, null));
        verify(trips, never()).delete(any(Trip.class));
    }

    @Test
    void memberCanLeaveWithoutChangingThePlan() {
        groupService.leave(FRIEND, id);

        assertTrue(friendMembership.isHidden());
        assertEquals(2, trip.getMembers().size());
        verify(trips, never()).delete(any(Trip.class));
    }

    @Test
    void organiserCannotLeaveTheirOwnTrip() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> groupService.leave(ORGANISER, id));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void strangerCannotLeave() {
        when(members.findMembership(id, STRANGER)).thenReturn(Optional.empty());

        assertNotFound(() -> groupService.leave(STRANGER, id));
    }

    private static void assertNotFound(Runnable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
}

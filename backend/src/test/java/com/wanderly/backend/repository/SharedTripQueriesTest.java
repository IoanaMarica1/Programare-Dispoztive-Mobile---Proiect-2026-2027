package com.wanderly.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripMember;
import com.wanderly.backend.model.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The queries that decide who sees a shared trip, run against the real database. Each test rolls back,
 * so nothing is left behind.
 */
@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-that-is-long-enough-for-hs256-0123456789",
        "spring.flyway.ignore-migration-patterns=*:missing"
})
@Transactional
class SharedTripQueriesTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private TripRepository trips;
    @Autowired
    private TripMemberRepository members;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private String organiser;
    private String friend;
    private String stranger;
    private User ana;
    private User bogdan;

    @BeforeEach
    void setUp() {
        organiser = "ana-" + suffix + "@test.local";
        friend = "bogdan-" + suffix + "@test.local";
        stranger = "eve-" + suffix + "@test.local";
        ana = users.save(User.builder().email(organiser).passwordHash("x").name("Ana").address("Cluj").age(25).build());
        bogdan = users.save(User.builder().email(friend).passwordHash("x").name("Bogdan").address("Cluj").age(26).build());
        users.save(User.builder().email(stranger).passwordHash("x").name("Eve").address("Cluj").age(27).build());
    }

    private Trip groupTrip(boolean saved) {
        Trip trip = Trip.builder()
                .user(ana).destination("Rome").latitude(41.9).longitude(12.5)
                .startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 2))
                .dayStart(LocalTime.of(9, 0)).dayEnd(LocalTime.of(18, 0))
                .build();
        trip.addMember(TripMember.builder().user(ana).name("Ana").position(1).interests(Set.of(Interest.ART)).build());
        trip.addMember(TripMember.builder().user(bogdan).name("Bogdan").position(2).interests(Set.of(Interest.FOOD)).build());
        trip.addMember(TripMember.builder().name("Cris").position(3).interests(Set.of(Interest.NATURE)).build());
        if (saved) trip.markSaved();
        return trips.saveAndFlush(trip);
    }

    private List<UUID> listed(String email) {
        return trips.findSavedVisibleTo(email, PageRequest.of(0, 50)).map(Trip::getPublicId).getContent();
    }

    @Test
    void savedGroupTripIsSharedWithMembersOnly() {
        UUID id = groupTrip(true).getPublicId();

        assertTrue(listed(organiser).contains(id));
        assertTrue(listed(friend).contains(id));
        assertFalse(listed(stranger).contains(id));
        assertTrue(trips.findSavedForMember(id, friend).isPresent());
        assertTrue(trips.findSavedForMember(id, stranger).isEmpty());
        assertEquals(1, trips.findSavedVisibleTo(friend, PageRequest.of(0, 50)).getTotalElements());
    }

    @Test
    void draftsStayWithTheOrganiser() {
        UUID id = groupTrip(false).getPublicId();

        assertFalse(listed(organiser).contains(id));  // the list only has saved plans
        assertFalse(listed(friend).contains(id));
        assertTrue(trips.findSavedForMember(id, friend).isEmpty());
        assertTrue(members.findMembership(id, friend).isEmpty());
    }

    @Test
    void leavingHidesItOnlyForThatMember() {
        UUID id = groupTrip(true).getPublicId();

        members.findMembership(id, friend).orElseThrow().hide();
        members.flush();

        assertFalse(listed(friend).contains(id));
        assertTrue(trips.findSavedForMember(id, friend).isEmpty());
        assertTrue(members.findMembership(id, friend).isEmpty());
        assertTrue(listed(organiser).contains(id));
    }

    @Test
    void organiserHasNoMembershipToLeave() {
        UUID id = groupTrip(true).getPublicId();

        assertTrue(members.findMembership(id, organiser).isEmpty());
    }
}

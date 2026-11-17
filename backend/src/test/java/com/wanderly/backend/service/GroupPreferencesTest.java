package com.wanderly.backend.service;

import static com.wanderly.backend.model.Interest.ADVENTURE;
import static com.wanderly.backend.model.Interest.ART;
import static com.wanderly.backend.model.Interest.FOOD;
import static com.wanderly.backend.model.Interest.HISTORY;
import static com.wanderly.backend.model.Interest.NATURE;
import static com.wanderly.backend.model.Interest.NIGHTLIFE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Priority;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripMember;
import com.wanderly.backend.model.TripStop;
import com.wanderly.backend.service.GroupPreferences.Member;
import com.wanderly.backend.service.GroupPreferences.Profile;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupPreferencesTest {

    private static Member member(String name, Set<Interest> interests) {
        return member(name, interests, Set.of(), Set.of());
    }

    private static Member member(String name, Set<Interest> interests, Set<Priority> priorities, Set<Pace> paces) {
        return new Member(UUID.randomUUID(), name, interests, priorities, paces);
    }

    private static boolean covered(Member m, Profile p) {
        return m.interests().stream().anyMatch(p.interests()::contains);
    }

    // ---------------------------------------------------------------- three conflicting sample groups

    @Test
    void groupWithNothingInCommonStillCoversEveryone() {
        Member ana = member("Ana", Set.of(HISTORY, ART));
        Member bogdan = member("Bogdan", Set.of(NIGHTLIFE, FOOD));
        Member cris = member("Cris", Set.of(NATURE, ADVENTURE));

        Profile p = GroupPreferences.merge(List.of(ana, bogdan, cris));

        assertTrue(covered(ana, p));
        assertTrue(covered(bogdan, p));
        assertTrue(covered(cris, p));
        // One interest each is enough; the plan isn't spread over all six.
        assertEquals(3, p.interests().size());
    }

    @Test
    void majorityLeadsButTheOddOneOutKeepsOneInterest() {
        Member odd = member("Dan", Set.of(ADVENTURE, NIGHTLIFE));
        List<Member> group = List.of(
                member("Ana", Set.of(FOOD, HISTORY)), member("Bia", Set.of(FOOD, HISTORY)),
                member("Cris", Set.of(FOOD, HISTORY)), member("Eva", Set.of(FOOD, HISTORY)), odd);

        Profile p = GroupPreferences.merge(group);

        // Equal weight, so ties keep the enum order.
        assertEquals(List.of(HISTORY, FOOD), p.interests().subList(0, 2));
        assertEquals(3, p.interests().size());
        assertTrue(covered(odd, p));
    }

    @Test
    void conflictingPacesSettleOnTheFastestEveryoneAccepts() {
        List<Member> group = List.of(
                member("Ana", Set.of(ART), Set.of(Priority.CULTURE), Set.of(Pace.RELAXED)),
                member("Bogdan", Set.of(NIGHTLIFE), Set.of(Priority.SOCIAL, Priority.CULTURE),
                        Set.of(Pace.BALANCED, Pace.PACKED)),
                member("Cris", Set.of(ADVENTURE), Set.of(Priority.BUDGET), Set.of(Pace.PACKED)),
                member("Dan", Set.of(FOOD), Set.of(), Set.of()));  // skipped the pace step

        Profile p = GroupPreferences.merge(group);

        assertEquals(Set.of(Pace.RELAXED), p.paces());
        // CULTURE: 2 of 4 (half). BUDGET: one person, but it's a limit. SOCIAL: 1 of 4, dropped.
        assertEquals(Set.of(Priority.CULTURE, Priority.BUDGET), p.priorities());
        group.forEach(m -> assertTrue(covered(m, p), m.name()));
    }

    // ---------------------------------------------------------------- the rules underneath

    @Test
    void tickingEveryInterestDoesNotOutvoteTheOthers() {
        Member greedy = member("Ana", Set.of(Interest.values()));
        Member focused = member("Bogdan", Set.of(NATURE));

        Map<Interest, Double> w = GroupPreferences.weights(List.of(greedy, focused));

        assertEquals(1.0, w.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertTrue(w.get(NATURE) > 0.5);
        assertTrue(w.get(HISTORY) < 0.1);
        assertEquals(NATURE, GroupPreferences.merge(List.of(greedy, focused)).interests().get(0));
    }

    @Test
    void sharedInterestIsKeptEvenWithASmallWeight() {
        Member a = member("Ana", Set.of(HISTORY, ART, NATURE, FOOD));
        Member b = member("Bogdan", Set.of(ADVENTURE, NIGHTLIFE, FOOD, NATURE));

        assertTrue(GroupPreferences.merge(List.of(a, b)).interests().containsAll(List.of(FOOD, NATURE)));
    }

    @Test
    void noPaceWhenNobodyChoseOne() {
        assertTrue(GroupPreferences.pace(List.of(member("Ana", Set.of(ART)), member("Bia", Set.of(FOOD)))).isEmpty());
    }

    @Test
    void stopTagsFollowTheCategoryMap() {
        Member ana = member("Ana", Set.of(HISTORY));
        Member bogdan = member("Bogdan", Set.of(NIGHTLIFE));

        assertEquals(List.of(ana.id()), GroupPreferences.suitedMembers("Museum", List.of(ana, bogdan), TripService.CATEGORIES));
        assertEquals(List.of(bogdan.id()), GroupPreferences.suitedMembers("Food", List.of(ana, bogdan), TripService.CATEGORIES));
        assertTrue(GroupPreferences.suitedMembers(null, List.of(ana, bogdan), TripService.CATEGORIES).isEmpty());
    }

    @Test
    void fairnessIsTheLeastServedMembersShare() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertEquals(0.25, GroupPreferences.fairness(Map.of(a, 3, b, 1), 4), 1e-9);
        assertEquals(0.0, GroupPreferences.fairness(Map.of(a, 4, b, 0), 4), 1e-9);
    }

    // ---------------------------------------------------------------- prompt and response

    @Test
    void promptListsEveryoneWithoutTheirNames() {
        List<Member> group = List.of(member("Ana Popescu", Set.of(ART)), member("Bogdan", Set.of(FOOD, NATURE)));

        TripService.Travellers t = GroupTripService.travellers(GroupPreferences.merge(group), group);

        assertTrue(t.extraRules().contains("P1 likes ART"));
        assertTrue(t.extraRules().contains("P2 likes FOOD, NATURE"));
        assertTrue(t.extraRules().contains("Fairness"));
        assertFalse(t.extraRules().contains("Ana"));
        assertFalse(t.extraRules().contains("Bogdan"));
        assertTrue(t.extraRules().endsWith("\n"));
    }

    @Test
    void groupTripResponseTagsStopsAndScoresFairness() {
        Trip trip = Trip.builder()
                .destination("Rome").latitude(41.9).longitude(12.5)
                .startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 1))
                .dayStart(LocalTime.of(9, 0)).dayEnd(LocalTime.of(18, 0))
                .build();
        TripMember ana = TripMember.builder().name("Ana").position(1).interests(Set.of(HISTORY)).build();
        TripMember bogdan = TripMember.builder().name("Bogdan").position(2).interests(Set.of(FOOD)).build();
        trip.addMember(ana);
        trip.addMember(bogdan);
        trip.addStop(stop(1, "Colosseum", "Historical"));
        trip.addStop(stop(2, "Trattoria", "Food"));
        trip.addStop(stop(3, "Pantheon", "Landmark"));

        TripResponse r = TripService.toResponse(trip);

        assertEquals(List.of(ana.getPublicId()), r.stops().get(0).suitedTo());
        assertEquals(0.5, r.stops().get(0).matchScore(), 1e-9);
        assertEquals(List.of(bogdan.getPublicId()), r.stops().get(1).suitedTo());
        assertEquals(2, r.members().get(0).matchedStops());
        assertEquals(1, r.members().get(1).matchedStops());
        assertEquals(1.0 / 3, r.fairness(), 1e-9);
        // Entered by hand, so their interests can be shown.
        assertEquals(Set.of(HISTORY), r.members().get(0).interests());
    }

    @Test
    void soloTripResponseHasNoGroupFields() {
        Trip trip = Trip.builder()
                .destination("Rome").latitude(41.9).longitude(12.5)
                .startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 1))
                .dayStart(LocalTime.of(9, 0)).dayEnd(LocalTime.of(18, 0))
                .build();
        trip.addStop(stop(1, "Colosseum", "Historical"));

        TripResponse r = TripService.toResponse(trip);

        assertTrue(r.members().isEmpty());
        assertNull(r.fairness());
        assertNull(r.stops().get(0).matchScore());
        assertTrue(r.stops().get(0).suitedTo().isEmpty());
    }

    private static TripStop stop(int position, String name, String category) {
        return TripStop.builder().dayNumber(1).position(position).name(name).category(category)
                .latitude(41.9).longitude(12.5).startTime(LocalTime.of(9 + position, 0)).durationMinutes(45)
                .build();
    }
}

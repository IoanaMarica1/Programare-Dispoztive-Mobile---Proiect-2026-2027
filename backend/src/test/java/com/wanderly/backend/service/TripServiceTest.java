package com.wanderly.backend.service;

import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.service.GeocodingClient.Place;
import com.wanderly.backend.service.TripService.AiStop;
import com.wanderly.backend.service.TripService.Picked;
import com.wanderly.backend.service.TripService.PlannedStop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripServiceTest {

    // 2026-11-09 is a Monday.
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final int NINE = 9 * 60, EIGHT_PM = 20 * 60;

    private static final List<Place> POOL = List.of(
            new Place(1, "Louvre", "Museum", 48.8606, 2.3376, "We-Mo 09:00-18:00"),
            new Place(2, "Musee d'Orsay", "Museum", 48.8600, 2.3266, "Tu-Su 09:30-18:00"),
            new Place(3, "Jardin du Luxembourg", "Nature", 48.8462, 2.3372, null),
            new Place(4, "Le Bistrot", "Food", 48.8530, 2.3400, "Mo-Su 12:00-23:30"),
            new Place(5, "Sainte-Chapelle", "Historical", 48.8554, 2.3450, null),
            new Place(6, "Notre-Dame", "Historical", 48.8530, 2.3499, null),
            new Place(7, "Pont Neuf", "Landmark", 48.8570, 2.3413, null),
            new Place(8, "Tuileries", "Nature", 48.8635, 2.3275, null),
            new Place(9, "Cafe de Flore", "Food", 48.8542, 2.3325, "Mo-Su 07:30-01:30"));
    private static final Map<Integer, Place> BY_ID =
            POOL.stream().collect(Collectors.toMap(Place::id, Function.identity()));

    private static List<PlannedStop> plan(int days, int perDay, int windowStart, int windowEnd, AiStop... stops) {
        List<Picked> picked = TripService.validStops(List.of(stops), BY_ID, days);
        return TripService.planDays(picked, POOL, MONDAY, days, windowStart, windowEnd, perDay);
    }

    private static List<PlannedStop> day(List<PlannedStop> plan, int day) {
        return plan.stream().filter(p -> p.day() == day).toList();
    }

    private static List<String> names(List<PlannedStop> stops) {
        return stops.stream().map(p -> p.place().name()).toList();
    }

    @Test
    @DisplayName("Stopuri suprapuse sunt re-programate cu timp de mers între ele, nu suprapuse")
    void planDays_retimesOverlapsWithWalkingTime() {
        List<PlannedStop> day1 = day(plan(1, 5, NINE, EIGHT_PM,
                new AiStop(1, 1, "10:00", 180, "Louvre"),
                new AiStop(1, 3, "11:00", 120, "Overlaps the Louvre")), 1);

        assertThat(names(day1)).containsExactlyInAnyOrder("Louvre", "Jardin du Luxembourg");
        PlannedStop first = day1.get(0), second = day1.get(1);
        assertThat(second.start()).isGreaterThanOrEqualTo(first.start() + first.minutes() + 20);  // ~1.6 km walk
        assertThat(first.position()).isEqualTo(1);
        assertThat(second.position()).isEqualTo(2);
    }

    @Test
    @DisplayName("Ora modelului nu contează: un stop care ar trece de miezul nopții e mutat când locul e deschis")
    void planDays_neverRunsPastTheWindow() {
        List<PlannedStop> day1 = day(plan(1, 5, 8 * 60, 23 * 60 + 30,
                new AiStop(1, 4, "22:30", 120, "Late dinner")), 1);

        assertThat(day1).singleElement().satisfies(p -> {
            assertThat(p.start()).isEqualTo(12 * 60);  // Le Bistrot opens at noon
            assertThat(p.start() + p.minutes()).isLessThanOrEqualTo(23 * 60 + 30);
        });
    }

    @Test
    @DisplayName("Loc închis în ziua lui (Orsay luni) e scos; marți rămâne")
    void planDays_dropsStopOnItsClosedDay() {
        List<PlannedStop> plan = plan(2, 5, NINE, EIGHT_PM,
                new AiStop(1, 2, "10:00", 120, "Closed on Mondays"),
                new AiStop(1, 1, "13:00", 120, "Louvre"),
                new AiStop(2, 6, "10:00", 60, "Notre-Dame"));

        assertThat(names(day(plan, 1))).containsExactly("Louvre");
        assertThat(names(day(plan, 2))).contains("Notre-Dame").doesNotContain("Musee d'Orsay");
    }

    @Test
    @DisplayName("Ritmul e respectat: relaxed (3 pe zi) păstrează doar primele 3 stopuri ale modelului")
    void planDays_capsStopsAtThePace() {
        List<PlannedStop> day1 = day(plan(1, 3, NINE, EIGHT_PM,
                new AiStop(1, 5, "09:00", 30, "a"), new AiStop(1, 6, "10:00", 30, "b"),
                new AiStop(1, 7, "11:00", 30, "c"), new AiStop(1, 9, "12:00", 45, "d"),
                new AiStop(1, 3, "14:00", 30, "e")), 1);

        assertThat(names(day1)).containsExactlyInAnyOrder("Sainte-Chapelle", "Notre-Dame", "Pont Neuf");
    }

    @Test
    @DisplayName("Zi lăsată goală de model → completată cu locuri nefolosite, deschise în ziua aceea")
    void planDays_fillsEmptyDay() {
        List<PlannedStop> plan = plan(2, 5, NINE, EIGHT_PM,
                new AiStop(1, 1, "10:00", 120, "Louvre"));

        List<PlannedStop> day2 = day(plan, 2);
        assertThat(day2).hasSizeBetween(1, TripService.FILL_STOPS + 1);
        assertThat(names(day2)).doesNotContain("Louvre");
        assertThat(day2).allSatisfy(p -> assertThat(p.start() + p.minutes()).isLessThanOrEqualTo(EIGHT_PM));
        assertThat(day2.stream().filter(p -> p.place().category().equals("Food")))
                .allSatisfy(p -> assertThat(p.start()).isBetween(11 * 60 + 30, 14 * 60 + 30));
    }

    @Test
    @DisplayName("Verificări de structură: durate nerealiste, zile din afara trip-ului, id-uri necunoscute, duplicate")
    void validStops_dropsBadStops() {
        List<Picked> picked = TripService.validStops(List.of(
                new AiStop(1, 3, "09:00", 5, "Too short"),
                new AiStop(1, 3, "10:00", 600, "Too long"),
                new AiStop(3, 3, "10:00", 60, "Day 3 of a 2-day trip"),
                new AiStop(1, 99, "10:00", 60, "Unknown place"),
                new AiStop(1, 3, "12:00", 60, "First use"),
                new AiStop(2, 3, "12:00", 60, "Duplicate"),
                new AiStop(1, 5, "bad", 60, "Bad time is fine, it sorts last")), BY_ID, 2);

        assertThat(picked).extracting(p -> p.ai().description())
                .containsExactly("First use", "Bad time is fine, it sorts last");
    }

    @Test
    @DisplayName("Un stop malformat e sărit, restul planului rămâne")
    void parseStops_skipsMalformedStop() {
        TripService service = new TripService(null, null, null, null, null);
        String json = """
                {"stops":[
                  {"day":1,"id":1,"start":"10:00","durationMinutes":120,"description":"ok"},
                  {"day":1,"id":2,"start":"13:00","durationMinutes":"60 min"},
                  {"day":1,"id":3,"start":"15:00","durationMinutes":null,"extra":"ignored"},
                  {"day":2,"id":4,"start":"12:00","durationMinutes":60,"rating":5}
                ]}""";

        List<AiStop> stops = service.parseStops(json);

        assertThat(stops).extracting(AiStop::id).containsExactly(1, 3, 4);
        assertThat(stops.get(1).durationMinutes()).isNull();
        assertThat(TripService.validStops(stops, BY_ID, 2)).extracting(p -> p.ai().id()).containsExactly(1, 4);
    }

    @Test
    @DisplayName("Ora fără zero în față (9:30), listă la rădăcină și JSON împachetat în ```json sunt acceptate")
    void parseStops_acceptsLooseFormats() {
        TripService service = new TripService(null, null, null, null, null);
        String text = """
                ```json
                [{"day":1,"id":3,"start":"9:30","durationMinutes":60}]
                ```""";

        List<Picked> picked = TripService.validStops(service.parseStops(text), BY_ID, 2);

        assertThat(picked).singleElement().satisfies(p -> assertThat(p.start()).hasToString("09:30"));
    }

    @Test
    @DisplayName("Răspuns care nu e JSON → excepție (devine 503 cu retry în aplicație)")
    void parseStops_rejectsNonJson() {
        TripService service = new TripService(null, null, null, null, null);

        assertThatThrownBy(() -> service.parseStops("Sorry, I can't help with that."))
                .isInstanceOf(RuntimeException.class);
        assertThat(service.parseStops("{\"plan\":[]}")).isEmpty();
    }

    private static List<Place> manyPlaces() {
        List<Place> places = new ArrayList<>();
        String[] categories = {"Museum", "Nature", "Landmark", "Food", "Historical", "Art"};
        for (int i = 1; i <= 120; i++) {
            places.add(new Place(i, "Place " + i, categories[i % categories.length], 0, 0, null));
        }
        return places;
    }

    @Test
    @DisplayName("Shortlist: cel mult ~1.5 locuri per stop, cu mâncare pentru mese și mai ales interesele")
    void shortlist_prefersInterestsAndKeepsFood() {
        List<Place> picked = TripService.shortlist(manyPlaces(), Set.of(Interest.NATURE), 2, 5);

        assertThat(picked).hasSize(TripService.MIN_SHORTLIST);
        // 2 days x 2 meals first, plus one more when the leftovers are shared out in turn.
        assertThat(picked.stream().filter(p -> p.category().equals("Food"))).hasSize(5);
        assertThat(picked.stream().filter(p -> p.category().equals("Nature"))).hasSize(20);
        assertThat(picked).extracting(Place::id).isSorted().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Shortlist: trip lung → mai multe locuri, plafonat; puține locuri → toate")
    void shortlist_sizeLimits() {
        assertThat(TripService.shortlist(manyPlaces(), Set.of(), 14, 7)).hasSize(TripService.MAX_SHORTLIST);
        assertThat(TripService.shortlist(manyPlaces().subList(0, 10), Set.of(Interest.ART), 3, 5)).hasSize(10);
        assertThat(TripService.stopsPerDay(Set.of())).isEqualTo(5);
        assertThat(TripService.stopsPerDay(Set.of(Pace.RELAXED, Pace.PACKED))).isEqualTo(7);
    }
}

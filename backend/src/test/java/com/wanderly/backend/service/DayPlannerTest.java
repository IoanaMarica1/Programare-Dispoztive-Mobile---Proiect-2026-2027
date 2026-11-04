package com.wanderly.backend.service;

import com.wanderly.backend.service.DayPlanner.Stop;
import com.wanderly.backend.service.DayPlanner.Timed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DayPlannerTest {

    private static final int NINE = 9 * 60, SIX_PM = 18 * 60;

    // Points on one street, ~1 km apart (0.009° of latitude).
    private static Stop at(int key, double km, String category, String hours, int minutes) {
        return new Stop(key, "Stop " + key, category, 45.0 + km * 0.009, 25.0, hours, minutes);
    }

    private static List<Integer> order(List<Timed> plan) {
        return plan.stream().map(t -> t.stop().key()).toList();
    }

    @Test
    @DisplayName("Ordinea cu cel mai puțin mers: A(0 km), C(2 km), B(1 km) → A, B, C")
    void plan_ordersForLeastWalking() {
        List<Timed> plan = DayPlanner.plan(List.of(
                at(1, 0, "Museum", null, 60), at(3, 2, "Museum", null, 60), at(2, 1, "Museum", null, 60)),
                DayOfWeek.MONDAY, NINE, SIX_PM).orElseThrow();

        assertThat(order(plan)).isIn(List.of(1, 2, 3), List.of(3, 2, 1));
        for (int i = 1; i < plan.size(); i++) {
            Timed prev = plan.get(i - 1), cur = plan.get(i);
            assertThat(cur.start()).isGreaterThanOrEqualTo(
                    prev.end() + DayPlanner.travelMinutes(prev.stop(), cur.stop()));
        }
    }

    @Test
    @DisplayName("Muzeu care se deschide la 10:00 într-o zi care începe la 9:00 → începe la 10:00")
    void plan_waitsForOpening() {
        List<Timed> plan = DayPlanner.plan(List.of(at(1, 0, "Museum", "Mo-Su 10:00-18:00", 90)),
                DayOfWeek.MONDAY, NINE, SIX_PM).orElseThrow();

        assertThat(plan.get(0).start()).isEqualTo(10 * 60);
    }

    @Test
    @DisplayName("Restaurantul ajunge la ora prânzului, nu la 9 dimineața")
    void plan_putsFoodAtMealTime() {
        List<Timed> plan = DayPlanner.plan(List.of(
                at(1, 0, "Food", null, 60), at(2, 0.1, "Museum", null, 120), at(3, 0.2, "Landmark", null, 60)),
                DayOfWeek.MONDAY, NINE, SIX_PM).orElseThrow();

        Timed lunch = plan.stream().filter(t -> t.stop().key() == 1).findFirst().orElseThrow();
        assertThat(lunch.start()).isBetween(11 * 60 + 30, 14 * 60 + 30);
    }

    @Test
    @DisplayName("Ce nu încape în zi: plan() → gol; planDropping() renunță la ultimele stopuri")
    void plan_infeasible() {
        List<Stop> stops = List.of(at(1, 0, "Museum", null, 240), at(2, 0.1, "Museum", null, 240),
                at(3, 0.2, "Museum", null, 240));

        assertThat(DayPlanner.plan(stops, DayOfWeek.MONDAY, NINE, SIX_PM)).isEmpty();
        assertThat(order(DayPlanner.planDropping(stops, DayOfWeek.MONDAY, NINE, SIX_PM)))
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("Loc închis în ziua aceea → nu poate fi planificat")
    void plan_closedPlace() {
        assertThat(DayPlanner.plan(List.of(at(1, 0, "Museum", "Tu-Su 10:00-18:00", 60)),
                DayOfWeek.MONDAY, NINE, SIX_PM)).isEmpty();
    }

    @Test
    @DisplayName("Timp de mers: 1 km în linie dreaptă ≈ 18 minute pe jos (cu ocolișuri)")
    void travelMinutes() {
        assertThat(DayPlanner.travelMinutes(at(1, 0, null, null, 0), at(2, 1, null, null, 0))).isBetween(16, 19);
    }

    @Test
    @DisplayName("8 stopuri (maximul) se planifică rapid")
    void plan_eightStopsIsFast() {
        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < DayPlanner.MAX_EXACT; i++) stops.add(at(i, (i * 7 % 8) * 0.3, "Landmark", null, 30));

        long started = System.nanoTime();
        assertThat(DayPlanner.plan(stops, DayOfWeek.MONDAY, 8 * 60, 22 * 60)).isPresent();
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2000);
    }
}

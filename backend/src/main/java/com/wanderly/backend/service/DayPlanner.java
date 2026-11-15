package com.wanderly.backend.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orders and times one day of a trip: the visiting order with the least walking that still fits the
 * daily window and every place's opening hours, waiting for a place to open when needed. Used after
 * Gemini proposes a plan and whenever the traveler swaps, removes or adds a stop.
 */
final class DayPlanner {

    /** A stop to plan. {@code key} lets the caller find its own object again in the result. */
    record Stop(int key, String name, String category, double lat, double lon, String openingHours, int minutes) {}

    /** A planned stop: where it is in the day and when it starts (minutes from midnight). */
    record Timed(Stop stop, int start) {
        int end() {
            return start + stop.minutes();
        }
    }

    /** Up to this many stops every order is tried (8! = 40320); beyond it the given order is kept. */
    static final int MAX_EXACT = 8;

    private static final double WALK_METERS_PER_MINUTE = 75;  // ~4.5 km/h
    private static final double DETOUR = 1.3;                 // streets are not straight lines
    private static final int[][] MEALS = {{11 * 60 + 30, 14 * 60 + 30}, {18 * 60, 21 * 60 + 30}};
    private static final int OFF_MEAL_PENALTY = 60;           // minutes of "cost" for lunch at 9:00

    private DayPlanner() {}

    /**
     * The cheapest feasible plan for these stops, or empty when they can't all fit. Cost is walking
     * time, plus half of any waiting for a place to open, plus a penalty for Food stops outside meal times.
     */
    static Optional<List<Timed>> plan(List<Stop> stops, DayOfWeek day, int windowStart, int windowEnd) {
        if (stops.isEmpty()) return Optional.of(List.of());
        if (stops.size() > MAX_EXACT) {
            Result r = simulate(stops, day, windowStart, windowEnd);
            return r == null ? Optional.empty() : Optional.of(r.timed);
        }
        Search search = new Search(stops, day, windowStart, windowEnd);
        search.run(new ArrayList<>(), new boolean[stops.size()], windowStart, null, 0);
        return Optional.ofNullable(search.best);
    }

    /**
     * Like {@link #plan}, but when not everything fits: first leaves out stops that can't be planned
     * even on their own (closed that day, longer than the window), then stops from the end of the
     * list (the caller puts the least important last) until the rest fits.
     */
    static List<Timed> planDropping(List<Stop> stops, DayOfWeek day, int windowStart, int windowEnd) {
        List<Stop> left = new ArrayList<>();
        for (Stop s : stops) {
            if (simulate(List.of(s), day, windowStart, windowEnd) != null) left.add(s);
        }
        while (!left.isEmpty()) {
            Optional<List<Timed>> plan = plan(left, day, windowStart, windowEnd);
            if (plan.isPresent()) return plan.get();
            left.remove(left.size() - 1);
        }
        return List.of();
    }

    static int travelMinutes(Stop from, Stop to) {
        double meters = Geo.distanceMeters(from.lat(), from.lon(), to.lat(), to.lon()) * DETOUR;
        return (int) Math.ceil(meters / WALK_METERS_PER_MINUTE);
    }

    private record Result(List<Timed> timed, double cost) {}

    /** Times the stops in the given order; null if one doesn't fit. */
    private static Result simulate(List<Stop> order, DayOfWeek day, int windowStart, int windowEnd) {
        List<Timed> timed = new ArrayList<>();
        int t = windowStart;
        Stop prev = null;
        double cost = 0;
        for (Stop s : order) {
            int travel = prev == null ? 0 : travelMinutes(prev, s);
            int arrive = t + travel;
            int start = OpeningHours.earliestStart(s.openingHours(), day, arrive, s.minutes());
            if (start < 0 || start + s.minutes() > windowEnd) return null;
            cost += travel + (start - arrive) / 2.0 + mealPenalty(s, start);
            timed.add(new Timed(s, start));
            t = start + s.minutes();
            prev = s;
        }
        return new Result(timed, cost);
    }

    private static double mealPenalty(Stop s, int start) {
        if (!"Food".equals(s.category())) return 0;
        for (int[] meal : MEALS) {
            if (start >= meal[0] && start <= meal[1]) return 0;
        }
        return OFF_MEAL_PENALTY;
    }

    /** Depth-first over every order, abandoning a branch as soon as it costs more than the best so far. */
    private static final class Search {
        final List<Stop> stops;
        final DayOfWeek day;
        final int windowStart, windowEnd;
        List<Timed> best;
        double bestCost = Double.MAX_VALUE;

        Search(List<Stop> stops, DayOfWeek day, int windowStart, int windowEnd) {
            this.stops = stops;
            this.day = day;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        void run(List<Timed> sofar, boolean[] used, int t, Stop prev, double cost) {
            if (cost >= bestCost) return;
            if (sofar.size() == stops.size()) {
                best = List.copyOf(sofar);
                bestCost = cost;
                return;
            }
            for (int i = 0; i < stops.size(); i++) {
                if (used[i]) continue;
                Stop s = stops.get(i);
                int travel = prev == null ? 0 : travelMinutes(prev, s);
                int arrive = t + travel;
                int start = OpeningHours.earliestStart(s.openingHours(), day, arrive, s.minutes());
                if (start < 0 || start + s.minutes() > windowEnd) continue;
                used[i] = true;
                sofar.add(new Timed(s, start));
                run(sofar, used, start + s.minutes(), s,
                        cost + travel + (start - arrive) / 2.0 + mealPenalty(s, start));
                sofar.remove(sofar.size() - 1);
                used[i] = false;
            }
        }
    }
}

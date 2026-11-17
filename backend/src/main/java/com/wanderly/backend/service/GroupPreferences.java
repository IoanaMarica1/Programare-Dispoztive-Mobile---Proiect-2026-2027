package com.wanderly.backend.service;

import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Priority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Merges the preferences of everyone on a group trip into one profile for the planner, and scores how
 * well each stop suits each member.
 *
 * <h2>Weighted overlap</h2>
 * Every member has one vote, split evenly across the interests they ticked. Someone who ticks all six
 * interests therefore doesn't outweigh someone who ticks one. An interest's weight is its share of all
 * votes (the weights add up to 1).
 *
 * <h2>Fairness rule</h2>
 * <ul>
 *   <li>Interests: the group keeps every interest shared by at least two members or with at least
 *       {@link #MIN_WEIGHT} of the votes. A member none of whose interests made the cut gets their
 *       most popular interest added anyway, so nobody is left without stops for them.</li>
 *   <li>Pace: the fastest pace that every member accepts. Nobody is pushed beyond the pace they picked.</li>
 *   <li>Priorities: those picked by at least half the group, plus BUDGET if anyone needs it.</li>
 * </ul>
 */
public final class GroupPreferences {

    /** An interest with at least this share of the votes is kept even if only one member has it. */
    static final double MIN_WEIGHT = 0.25;

    private GroupPreferences() {
    }

    /** The preferences of one person on the trip. */
    public record Member(UUID id, String name, Set<Interest> interests, Set<Priority> priorities, Set<Pace> paces) {
        public Member {
            interests = interests == null ? Set.of() : Set.copyOf(interests);
            priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
            paces = paces == null ? Set.of() : Set.copyOf(paces);
        }
    }

    /**
     * The merged profile. {@code interests} is ordered most shared first; {@code weights} has every
     * interest any member chose. {@code paces} holds one pace, or none when nobody chose one.
     */
    public record Profile(List<Interest> interests, Map<Interest, Double> weights,
                          Set<Priority> priorities, Set<Pace> paces) {
    }

    public static Profile merge(List<Member> members) {
        if (members.isEmpty()) throw new IllegalArgumentException("A group needs members");

        Map<Interest, Double> weights = weights(members);
        Map<Interest, Integer> counts = new EnumMap<>(Interest.class);
        for (Member m : members) m.interests().forEach(i -> counts.merge(i, 1, Integer::sum));

        Set<Interest> chosen = EnumSet.noneOf(Interest.class);
        weights.forEach((interest, weight) -> {
            if (counts.get(interest) >= 2 || weight >= MIN_WEIGHT) chosen.add(interest);
        });
        // Fairness: everyone keeps at least one of their own interests.
        for (Member m : members) {
            if (m.interests().isEmpty() || m.interests().stream().anyMatch(chosen::contains)) continue;
            m.interests().stream()
                    .max(Comparator.comparingDouble((Interest i) -> weights.get(i))
                            .thenComparing(Comparator.reverseOrder()))
                    .ifPresent(chosen::add);
        }
        List<Interest> ordered = new ArrayList<>(chosen);
        ordered.sort(Comparator.comparingDouble((Interest i) -> -weights.get(i)).thenComparing(i -> i));

        return new Profile(ordered, weights, priorities(members), pace(members));
    }

    /** Each member's vote split evenly over their interests; the shares add up to 1. */
    static Map<Interest, Double> weights(List<Member> members) {
        Map<Interest, Double> weights = new EnumMap<>(Interest.class);
        long voters = members.stream().filter(m -> !m.interests().isEmpty()).count();
        if (voters == 0) return weights;
        for (Member m : members) {
            double share = 1.0 / m.interests().size() / voters;
            m.interests().forEach(i -> weights.merge(i, share, Double::sum));
        }
        return weights;
    }

    /** The fastest pace everyone accepts; members who skipped the pace step don't limit it. */
    static Set<Pace> pace(List<Member> members) {
        Pace slowestMax = null;
        for (Member m : members) {
            if (m.paces().isEmpty()) continue;
            Pace fastestForThem = m.paces().stream().max(Comparator.naturalOrder()).orElseThrow();
            if (slowestMax == null || fastestForThem.compareTo(slowestMax) < 0) slowestMax = fastestForThem;
        }
        return slowestMax == null ? Set.of() : Set.of(slowestMax);
    }

    /** Majority priorities, plus BUDGET when anyone needs it (it's a limit, not a taste). */
    static Set<Priority> priorities(List<Member> members) {
        Map<Priority, Integer> counts = new EnumMap<>(Priority.class);
        for (Member m : members) m.priorities().forEach(p -> counts.merge(p, 1, Integer::sum));
        Set<Priority> result = EnumSet.noneOf(Priority.class);
        counts.forEach((p, n) -> {
            if (n * 2 >= members.size() || p == Priority.BUDGET) result.add(p);
        });
        return result;
    }

    // ---------------------------------------------------------------- matching stops to members

    /** Whether a stop of this category matches any of the member's interests. */
    static boolean suits(String category, Set<Interest> interests, Map<Interest, Set<String>> categories) {
        if (category == null) return false;
        for (Interest i : interests) {
            if (categories.getOrDefault(i, Set.of()).contains(category)) return true;
        }
        return false;
    }

    /** The ids of the members a stop suits, in member order. */
    static List<UUID> suitedMembers(String category, Collection<Member> members,
                                    Map<Interest, Set<String>> categories) {
        List<UUID> ids = new ArrayList<>();
        for (Member m : members) {
            if (suits(category, m.interests(), categories)) ids.add(m.id());
        }
        return ids;
    }

    /**
     * How fair a plan is: the smallest share of stops that suit any one member (0 to 1). 0 means
     * someone has nothing for them; 1 means every stop suits everyone.
     */
    static double fairness(Map<UUID, Integer> matchedStops, int totalStops) {
        if (totalStops == 0 || matchedStops.isEmpty()) return 0;
        int least = matchedStops.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        return (double) least / totalStops;
    }
}

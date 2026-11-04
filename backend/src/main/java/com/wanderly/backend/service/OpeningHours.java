package com.wanderly.backend.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a visit against an OSM opening_hours tag (e.g. "Mo-Fr 09:00-18:00; Sa 10:00-14:00").
 * Only the common subset is understood: weekdays, day ranges and lists, time ranges, "off" and "24/7".
 * Anything else (months, holidays with times, sunrise, comments...) is UNKNOWN, and callers keep the stop.
 */
final class OpeningHours {

    enum Result { OPEN, CLOSED, UNKNOWN }

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "Mo", DayOfWeek.MONDAY, "Tu", DayOfWeek.TUESDAY, "We", DayOfWeek.WEDNESDAY,
            "Th", DayOfWeek.THURSDAY, "Fr", DayOfWeek.FRIDAY, "Sa", DayOfWeek.SATURDAY,
            "Su", DayOfWeek.SUNDAY);

    private static final String DAY = "(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)";
    private static final String TIME = "\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}";
    private static final Pattern RULE = Pattern.compile(
            "^(?:(" + DAY + "(?:-" + DAY + ")?(?:\\s*,\\s*" + DAY + "(?:-" + DAY + ")?)*)\\s+)?"
                    + "(off|closed|" + TIME + "(?:\\s*,\\s*" + TIME + ")*)$");
    // "Mo-Fr 09:00-18:00, Sa 10:00-14:00": a comma before a new day selector starts a new rule.
    private static final Pattern RULE_COMMA = Pattern.compile("(?<=\\d)\\s*,\\s*(?=" + DAY + "\\b)");
    private static final int DAY_MINUTES = 24 * 60;

    private OpeningHours() {}

    /** Whether the place is open for the whole visit [startMinute, endMinute) on that day (minutes from midnight). */
    static Result check(String spec, DayOfWeek day, int startMinute, int endMinute) {
        Map<DayOfWeek, List<int[]>> week = parse(spec);
        if (week == null) return Result.UNKNOWN;
        // Days the tag doesn't mention are closed.
        for (int[] r : week.getOrDefault(day, List.of())) {
            if (r[0] <= startMinute && endMinute <= r[1]) return Result.OPEN;
        }
        return Result.CLOSED;
    }

    /**
     * The earliest start, not before {@code from}, at which a visit of {@code minutes} fits inside the
     * opening hours that day; {@code from} itself when the hours are unknown, -1 when it never fits.
     */
    static int earliestStart(String spec, DayOfWeek day, int from, int minutes) {
        Map<DayOfWeek, List<int[]>> week = parse(spec);
        if (week == null) return from;
        int best = -1;
        for (int[] r : week.getOrDefault(day, List.of())) {
            int start = Math.max(from, r[0]);
            if (start + minutes <= r[1] && (best < 0 || start < best)) best = start;
        }
        return best;
    }

    /** Opening ranges per weekday (minutes from midnight), or null when the tag can't be understood. */
    private static Map<DayOfWeek, List<int[]>> parse(String spec) {
        if (spec == null || spec.isBlank()) return null;
        String s = spec.trim();
        Map<DayOfWeek, List<int[]>> week = new EnumMap<>(DayOfWeek.class);
        if (s.equals("24/7")) {
            for (DayOfWeek d : DayOfWeek.values()) week.put(d, List.of(new int[]{0, DAY_MINUTES}));
            return week;
        }
        if (s.contains("||") || s.contains("\"")) return null;

        // As in OSM: a rule after ";" replaces the hours of the days it names,
        // an "additional" rule after "," adds to them.
        for (String group : s.split(";")) {
            boolean additional = false;
            for (String raw : RULE_COMMA.split(group)) {
                String rule = raw.trim();
                if (rule.isEmpty()) continue;
                Matcher m = RULE.matcher(rule);
                if (!m.matches()) return null;

                Set<DayOfWeek> days = m.group(1) == null ? EnumSet.allOf(DayOfWeek.class) : days(m.group(1));
                List<int[]> ranges = ranges(m.group(2));
                if (ranges == null) return null;
                for (DayOfWeek d : days) {  // empty for "PH off": public holidays aren't known here
                    if (additional && week.containsKey(d)) {
                        List<int[]> merged = new ArrayList<>(week.get(d));
                        merged.addAll(ranges);
                        week.put(d, merged);
                    } else {
                        week.put(d, ranges);
                    }
                }
                additional = true;
            }
        }
        return week.isEmpty() ? null : week;
    }

    private static Set<DayOfWeek> days(String selector) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : selector.split(",")) {
            String[] ends = part.trim().split("-");
            DayOfWeek from = DAYS.get(ends[0]);
            DayOfWeek to = ends.length > 1 ? DAYS.get(ends[1]) : from;
            if (from == null || to == null) continue;  // PH
            for (DayOfWeek d = from; ; d = d.plus(1)) {  // ranges may wrap, e.g. Fr-Mo
                days.add(d);
                if (d == to) break;
            }
        }
        return days;
    }

    /** "off" gives no ranges; an end at or before the start runs past midnight. Null if a time is invalid. */
    private static List<int[]> ranges(String times) {
        List<int[]> ranges = new ArrayList<>();
        if (times.equals("off") || times.equals("closed")) return ranges;
        for (String part : times.split(",")) {
            String[] ends = part.trim().split("-");
            int from = minutes(ends[0]);
            int to = minutes(ends[1]);
            if (from < 0 || to < 0 || from >= DAY_MINUTES) return null;
            if (to <= from) to += DAY_MINUTES;
            ranges.add(new int[]{from, to});
        }
        return ranges;
    }

    private static int minutes(String hhmm) {
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        return h > 24 || m > 59 || (h == 24 && m > 0) ? -1 : h * 60 + m;
    }
}

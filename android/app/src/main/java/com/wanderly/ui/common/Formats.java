package com.wanderly.ui.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;

public final class Formats {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE, d MMM");

    private Formats() {
    }

    /** "2026-11-12", "2026-11-15" → "12 Nov – 15 Nov 2026"; the year shows twice only if it changes. */
    public static String dateRange(String isoStart, String isoEnd) {
        if (isoStart == null || isoEnd == null) return "—";
        try {
            LocalDate start = LocalDate.parse(isoStart);
            LocalDate end = LocalDate.parse(isoEnd);
            if (start.equals(end)) return DAY_MONTH_YEAR.format(end);
            DateTimeFormatter first = start.getYear() == end.getYear() ? DAY_MONTH : DAY_MONTH_YEAR;
            return first.format(start) + " – " + DAY_MONTH_YEAR.format(end);
        } catch (DateTimeParseException e) {
            return "—";
        }
    }

    /** The date of a trip's day: "2026-11-12", day 2 → "Friday, 13 Nov". */
    public static String tripDay(String isoStart, int dayNumber) {
        if (isoStart == null) return "—";
        try {
            return WEEKDAY.format(LocalDate.parse(isoStart).plusDays(dayNumber - 1L));
        } catch (DateTimeParseException e) {
            return "—";
        }
    }

    /** "2026-10-12T09:30:00Z" → "12 Oct 2026" in the phone's language and time zone. */
    public static String date(String isoInstant) {
        if (isoInstant == null) return "—";
        try {
            return DATE.format(Instant.parse(isoInstant).atZone(ZoneId.systemDefault()));
        } catch (DateTimeParseException e) {
            return "—";
        }
    }
}

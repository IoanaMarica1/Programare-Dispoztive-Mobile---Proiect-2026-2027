package com.wanderly.ui.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;

public final class Formats {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private Formats() {
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

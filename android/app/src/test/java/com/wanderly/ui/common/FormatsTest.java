package com.wanderly.ui.common;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Locale;

public class FormatsTest {

    // Runs before Formats is loaded, because its formatters take the default language when created.
    @BeforeClass
    public static void useEnglish() {
        Locale.setDefault(Locale.UK);
    }

    @Test
    public void rangeInOneYearShowsTheYearOnce() {
        assertEquals("12 Nov – 15 Nov 2026", Formats.dateRange("2026-11-12", "2026-11-15"));
    }

    @Test
    public void rangeAcrossNewYearShowsBothYears() {
        assertEquals("30 Dec 2026 – 2 Jan 2027", Formats.dateRange("2026-12-30", "2027-01-02"));
    }

    @Test
    public void oneDayTripShowsOneDate() {
        assertEquals("12 Nov 2026", Formats.dateRange("2026-11-12", "2026-11-12"));
    }

    @Test
    public void unreadableDatesShowADash() {
        assertEquals("—", Formats.dateRange(null, "2026-11-12"));
        assertEquals("—", Formats.dateRange("soon", "2026-11-12"));
        assertEquals("—", Formats.tripDay(null, 1));
        assertEquals("—", Formats.tripDay("soon", 1));
    }

    @Test
    public void tripDayCountsFromTheStartDate() {
        assertEquals("Thursday, 12 Nov", Formats.tripDay("2026-11-12", 1));
        assertEquals("Saturday, 14 Nov", Formats.tripDay("2026-11-12", 3));
    }
}

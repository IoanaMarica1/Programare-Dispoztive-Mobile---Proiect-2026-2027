package com.wanderly.ui.common;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Locale;

public class FormatsDistanceTest {

    // Runs before Formats is loaded; kilometres use the phone's decimal separator.
    @BeforeClass
    public static void useEnglish() {
        Locale.setDefault(Locale.UK);
    }

    @Test
    public void shortDistancesAreExactMetres() {
        assertEquals("0 m", Formats.distance(0));
        assertEquals("85 m", Formats.distance(85));
        assertEquals("99 m", Formats.distance(99));
    }

    @Test
    public void distancesUnderAKilometreRoundToTens() {
        assertEquals("100 m", Formats.distance(100));
        assertEquals("450 m", Formats.distance(452));
        assertEquals("460 m", Formats.distance(455));
        assertEquals("990 m", Formats.distance(990));
    }

    @Test
    public void justUnderAKilometreIsNeverShownAsThousandMetres() {
        assertEquals("1.0 km", Formats.distance(996));
        assertEquals("1.0 km", Formats.distance(1000));
    }

    @Test
    public void longerDistancesUseKilometresWithOneDecimal() {
        assertEquals("1.2 km", Formats.distance(1234));
        assertEquals("2.0 km", Formats.distance(2000));
    }

    @Test
    public void negativeDistanceIsTreatedAsZero() {
        assertEquals("0 m", Formats.distance(-5));
    }
}

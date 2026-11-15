package com.wanderly.ui.surprise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Locale;

public class SurpriseLinksTest {

    @Test
    public void walkingNavigationAsksForTurnByTurnOnFoot() {
        assertEquals("google.navigation:q=46.770000,23.590000&mode=w",
                SurpriseLinks.walkingNavigation(46.77, 23.59));
    }

    @Test
    public void coordinatesUseADotWhateverThePhonesLanguage() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);   // would write "46,770000"
            assertEquals("google.navigation:q=46.770000,23.590000&mode=w",
                    SurpriseLinks.walkingNavigation(46.77, 23.59));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    public void negativeAndTinyCoordinatesStayPlainNumbers() {
        String link = SurpriseLinks.web(-33.8688, 0.00004);
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=-33.868800,0.000040&travelmode=walking", link);
        assertFalse(link.contains("E-"));
    }

    @Test
    public void geoLinkLabelsThePinWithTheEncodedName() {
        assertEquals("geo:0,0?q=46.770000,23.590000(Muzeul%20Na%C8%9Bional)",
                SurpriseLinks.geo(46.77, 23.59, "Muzeul Național"));
    }

    @Test
    public void bracketsInANameCannotEndTheLabelEarly() {
        assertEquals("geo:0,0?q=1.000000,2.000000(Caf%C3%A9%20Central%20old)",
                SurpriseLinks.geo(1, 2, "Café (Central) old"));
    }

    @Test
    public void ampersandsAndSlashesInANameAreEncoded() {
        String link = SurpriseLinks.geo(1, 2, "Bar & Grill/2");
        assertEquals("geo:0,0?q=1.000000,2.000000(Bar%20%26%20Grill%2F2)", link);
    }

    @Test
    public void missingNameGivesAnEmptyLabel() {
        assertEquals("geo:0,0?q=1.000000,2.000000()", SurpriseLinks.geo(1, 2, null));
    }
}

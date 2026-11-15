package com.wanderly.ui.surprise;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * The addresses "Take me there" can open, best first: turn-by-turn walking in Google Maps, any maps app
 * that handles geo: links, then the web. Kept apart from the activity so they can be tested.
 */
public final class SurpriseLinks {

    private SurpriseLinks() {
    }

    /** Google Maps turn-by-turn, on foot. */
    public static String walkingNavigation(double lat, double lon) {
        return "google.navigation:q=" + coordinates(lat, lon) + "&mode=w";
    }

    /** Any maps app: drops a pin labelled with the place's name. */
    public static String geo(double lat, double lon, String name) {
        return "geo:0,0?q=" + coordinates(lat, lon) + "(" + encode(name) + ")";
    }

    /** Works in a browser when no maps app is installed. */
    public static String web(double lat, double lon) {
        return "https://www.google.com/maps/dir/?api=1&destination=" + coordinates(lat, lon) + "&travelmode=walking";
    }

    /** "46.770000,23.590000": always a dot and never scientific notation, whatever the phone's language. */
    private static String coordinates(double lat, double lon) {
        return String.format(Locale.ROOT, "%.6f,%.6f", lat, lon);
    }

    /** Percent-encoded, with the brackets that would end the label early removed. */
    private static String encode(String name) {
        String label = (name == null ? "" : name).replace('(', ' ').replace(')', ' ').replaceAll("\\s+", " ").trim();
        try {
            return URLEncoder.encode(label, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);  // UTF-8 is always there
        }
    }
}

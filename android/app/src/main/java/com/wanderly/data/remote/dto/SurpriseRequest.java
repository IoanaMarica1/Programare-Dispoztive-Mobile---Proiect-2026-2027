package com.wanderly.data.remote.dto;

import java.util.List;

/** Asks for the best place within 2 km that fits a mood and is open now. */
public class SurpriseRequest {
    /** The backend's Mood name, e.g. "RELAXED". */
    public String mood;
    public double latitude;
    public double longitude;
    /** The phone's time zone, so "open now" is judged in local time. */
    public String timeZone;
    /** Places already shown, so "Another one" moves on to the next best. */
    public List<String> exclude;

    public SurpriseRequest(String mood, double latitude, double longitude, String timeZone, List<String> exclude) {
        this.mood = mood;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeZone = timeZone;
        this.exclude = exclude;
    }
}

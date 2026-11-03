package com.wanderly.data.remote.dto;

/** A place to add to a day (dayNumber required) or to swap in for a stop (dayNumber ignored). */
public class StopRequest {
    public Integer dayNumber;
    public String name;
    public String category;
    public Double latitude;
    public Double longitude;
    public String openingHours;
    public Integer durationMinutes;   // null: the backend picks one for the category

    public static StopRequest from(CandidateResponse c, Integer dayNumber) {
        StopRequest r = new StopRequest();
        r.dayNumber = dayNumber;
        r.name = c.name;
        r.category = c.category;
        r.latitude = c.latitude;
        r.longitude = c.longitude;
        r.openingHours = c.openingHours;
        return r;
    }
}

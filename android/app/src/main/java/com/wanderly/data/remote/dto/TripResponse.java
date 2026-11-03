package com.wanderly.data.remote.dto;

import java.util.List;

public class TripResponse {
    public String id;
    public String destination;
    public String country;
    public Double latitude;
    public Double longitude;
    public String startDate;
    public String endDate;
    public String dayStart;
    public String dayEnd;
    public List<Stop> stops;

    public static class Stop {
        public String id;
        public int dayNumber;
        public int position;
        public String name;
        public String category;
        public double latitude;
        public double longitude;
        public String startTime;
        public int durationMinutes;
        public String description;
        public String openingHours;
    }
}
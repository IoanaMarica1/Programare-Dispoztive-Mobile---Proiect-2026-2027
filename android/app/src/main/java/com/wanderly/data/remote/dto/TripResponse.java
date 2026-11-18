package com.wanderly.data.remote.dto;

import java.util.List;

/**
 * A trip. For a group trip {@code members} lists everyone (organiser first), each stop says who it
 * suits and {@code fairness} is the smallest share of stops suiting any one member (0 to 1).
 * Solo trips have no members. {@code owner} is false when a member views someone else's group trip.
 */
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
    public boolean saved;      // false: a draft that disappears unless the traveler saves it
    public List<Stop> stops;
    public List<Member> members;
    public Double fairness;
    public boolean owner = true;
    public String organiserName;

    public boolean isGroup() {
        return members != null && members.size() > 1;
    }

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
        public List<String> suitedTo;   // member ids
        public Double matchScore;       // their share of the group
    }

    /** {@code interests} are only sent for people entered by hand; userId is null for them. */
    public static class Member {
        public String id;
        public String name;
        public String userId;
        public String profilePicture;
        public List<String> interests;
        public int matchedStops;
    }
}

package com.wanderly.data.remote.dto;

/** One of the places Surprise Me suggests. Fields that are null are left off the card. */
public class SurprisePlaceResponse {
    /** OpenStreetMap reference ("node/123"); sent back to check in. */
    public String id;
    public String name;
    public String category;
    public double latitude;
    public double longitude;
    public int distanceMeters;
    public int walkingMinutes;
    /** One sentence on why it fits the mood. */
    public String why;
    /** "Open until 18:00", "Open-air, always accessible"... */
    public String openNote;
    public String photoUrl;
    public String address;
    public String website;
}

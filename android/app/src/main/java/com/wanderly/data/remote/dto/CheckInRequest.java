package com.wanderly.data.remote.dto;

/** The traveler is at a suggested place. The server finds the place itself and checks the distance. */
public class CheckInRequest {
    public String placeId;
    public double latitude;
    public double longitude;

    public CheckInRequest(String placeId, double latitude, double longitude) {
        this.placeId = placeId;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}

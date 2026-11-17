package com.wanderly.data.remote.dto;

/** A Wanderly user found by email. Their preferences stay private; only whether they exist is told. */
public class TravellerLookupResponse {
    public String userId;
    public String name;
    public String profilePicture;
    public boolean hasPreferences;
}

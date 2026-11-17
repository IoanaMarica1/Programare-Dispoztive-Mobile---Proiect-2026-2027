package com.wanderly.data.remote.dto;

/** Optional body of the regenerate call; without a pace the travellers' preferences decide it. */
public class RegenerateTripRequest {
    public String pace;

    public RegenerateTripRequest(String pace) {
        this.pace = pace;
    }
}

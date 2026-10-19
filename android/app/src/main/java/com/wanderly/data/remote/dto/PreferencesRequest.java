package com.wanderly.data.remote.dto;

import java.util.List;

/** Every question accepts several answers; priorities and paces are optional steps and may be empty. */
public class PreferencesRequest {
    public List<String> travelModes;
    public List<String> interests;
    public List<String> priorities;
    public List<String> paces;

    public PreferencesRequest(List<String> travelModes, List<String> interests,
                              List<String> priorities, List<String> paces) {
        this.travelModes = travelModes;
        this.interests = interests;
        this.priorities = priorities;
        this.paces = paces;
    }
}

package com.wanderly.data.remote.dto;

import java.util.List;

/** POST /api/trips/generate-group: the usual trip details plus the other travellers (1 to 7). */
public class GenerateGroupTripRequest {
    public GenerateTripRequest trip;
    public List<GroupMemberRequest> members;

    public GenerateGroupTripRequest(GenerateTripRequest trip, List<GroupMemberRequest> members) {
        this.trip = trip;
        this.members = members;
    }
}

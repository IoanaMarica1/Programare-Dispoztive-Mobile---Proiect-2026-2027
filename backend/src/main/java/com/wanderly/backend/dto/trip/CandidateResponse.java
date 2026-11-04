package com.wanderly.backend.dto.trip;

/** A real place near the trip that isn't in it yet, to add or swap in. */
public record CandidateResponse(
        String name,
        String category,
        double latitude,
        double longitude,
        String openingHours,
        int distanceMeters
) {}

package com.wanderly.backend.dto.visit;

import java.time.Instant;
import java.util.UUID;

public record VisitResponse(
        UUID publicId,
        String objectiveName,
        String city,
        String country,
        Double latitude,
        Double longitude,
        Instant visitedAt
) {
}

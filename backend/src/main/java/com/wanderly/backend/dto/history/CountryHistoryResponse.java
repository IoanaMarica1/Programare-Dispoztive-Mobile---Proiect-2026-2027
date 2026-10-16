package com.wanderly.backend.dto.history;

import java.time.Instant;

public record CountryHistoryResponse(
        String country,
        long objectivesVisited,
        Instant lastVisitedAt
) {
}

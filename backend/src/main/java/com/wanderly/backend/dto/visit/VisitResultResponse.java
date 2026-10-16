package com.wanderly.backend.dto.visit;

public record VisitResultResponse(
        VisitResponse visit,
        boolean firstVisit,
        int pointsAwarded,
        int totalPoints
) {
}

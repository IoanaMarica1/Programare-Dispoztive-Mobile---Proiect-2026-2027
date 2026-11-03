package com.wanderly.backend.dto.trip;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        UUID id,
        String destination,
        String country,
        Double latitude,
        Double longitude,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime dayStart,
        LocalTime dayEnd,
        List<StopResponse> stops
) {
    public record StopResponse(
            int dayNumber,
            int position,
            String name,
            String category,
            double latitude,
            double longitude,
            LocalTime startTime,
            int durationMinutes,
            String description,
            String openingHours
    ) {}
}
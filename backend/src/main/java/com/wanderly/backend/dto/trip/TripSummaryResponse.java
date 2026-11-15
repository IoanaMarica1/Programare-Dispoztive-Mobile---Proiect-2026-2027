package com.wanderly.backend.dto.trip;

import java.time.LocalDate;
import java.util.UUID;

/** One row in the list of saved plans; the stops load when the plan is opened. */
public record TripSummaryResponse(
        UUID id,
        String destination,
        String country,
        LocalDate startDate,
        LocalDate endDate
) {}

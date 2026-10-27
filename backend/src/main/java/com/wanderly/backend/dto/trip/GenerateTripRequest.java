package com.wanderly.backend.dto.trip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record GenerateTripRequest(
        @NotBlank String destination,
        String country,
        Double latitude,
        Double longitude,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalTime dayStart,
        @NotNull LocalTime dayEnd
) {}
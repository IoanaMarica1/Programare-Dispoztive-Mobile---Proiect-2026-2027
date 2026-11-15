package com.wanderly.backend.dto.trip;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A place to add to a day (POST, dayNumber required) or to put in place of a stop (PUT, dayNumber
 * ignored), usually one of GET /api/trips/{id}/candidates. durationMinutes defaults by category.
 */
public record StopRequest(
        @Min(value = 1, message = "Day must be at least 1.")
        Integer dayNumber,
        @NotBlank(message = "Name is required.")
        @Size(max = 200, message = "Name must not contain more than 200 characters.")
        String name,
        @Size(max = 50, message = "Category must not contain more than 50 characters.")
        String category,
        @NotNull(message = "Latitude is required.")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
        Double latitude,
        @NotNull(message = "Longitude is required.")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
        Double longitude,
        @Size(max = 255, message = "Opening hours must not contain more than 255 characters.")
        String openingHours,
        @Min(value = 15, message = "A visit lasts at least 15 minutes.")
        @Max(value = 480, message = "A visit lasts at most 8 hours.")
        Integer durationMinutes
) {}

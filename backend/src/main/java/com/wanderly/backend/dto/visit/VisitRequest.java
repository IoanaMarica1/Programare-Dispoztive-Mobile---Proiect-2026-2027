package com.wanderly.backend.dto.visit;

import jakarta.validation.constraints.*;

public record VisitRequest(

        @NotBlank(message = "Objective name is required.")
        @Size(max = 200, message = "Objective name must not contain more than 200 characters.")
        String objectiveName,

        @NotBlank(message = "City is required.")
        @Size(max = 100, message = "City must not contain more than 100 characters.")
        String city,

        @NotBlank(message = "Country is required.")
        @Size(max = 100, message = "Country must not contain more than 100 characters.")
        String country,

        @NotNull(message = "Latitude is required.")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
        Double latitude,

        @NotNull(message = "Longitude is required.")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
        Double longitude
) {
}

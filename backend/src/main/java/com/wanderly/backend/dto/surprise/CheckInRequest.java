package com.wanderly.backend.dto.surprise;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CheckInRequest(

        @NotBlank(message = "Place is required.")
        @Pattern(regexp = SurprisePlaceResponse.ID_PATTERN, message = "Invalid place id.")
        String placeId,

        // Where the traveler is now. The server looks the place up itself and accepts the
        // check-in only within VisitService.MAX_CHECK_IN_METERS of it.
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

package com.wanderly.backend.dto.surprise;

import com.wanderly.backend.model.Mood;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SurpriseRequest(

        @NotNull(message = "Pick a mood.")
        Mood mood,

        // Where the traveler is now (GPS).
        @NotNull(message = "Latitude is required.")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
        Double latitude,

        @NotNull(message = "Longitude is required.")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
        Double longitude,

        // The phone's time zone (e.g. "Europe/Bucharest"): "open now" is judged in the traveler's local time.
        @Size(max = 64, message = "Time zone is too long.")
        String timeZone,

        // Places already shown, so "Another one" moves on to the next best result.
        @Size(max = 50, message = "Too many places to skip.")
        List<@Pattern(regexp = SurprisePlaceResponse.ID_PATTERN, message = "Invalid place id.") String> exclude
) {
}

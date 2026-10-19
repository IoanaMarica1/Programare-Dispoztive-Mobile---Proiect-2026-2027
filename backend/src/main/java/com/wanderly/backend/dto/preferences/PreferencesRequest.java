package com.wanderly.backend.dto.preferences;

import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Priority;
import com.wanderly.backend.model.TravelMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

// Every question accepts several answers. Priorities and paces are optional wizard steps
// and may be empty or null.
public record PreferencesRequest(

        @NotEmpty(message = "Choose at least one travel mode.")
        Set<@NotNull TravelMode> travelModes,

        @NotEmpty(message = "Choose at least one interest.")
        Set<@NotNull Interest> interests,

        Set<@NotNull Priority> priorities,

        Set<@NotNull Pace> paces
) {
}

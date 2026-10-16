package com.wanderly.backend.dto.preferences;

import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Priority;
import com.wanderly.backend.model.TravelMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

// Priority and pace are optional wizard steps and may be null.
public record PreferencesRequest(

        @NotNull(message = "Travel mode is required.")
        TravelMode travelMode,

        @NotEmpty(message = "Choose at least one interest.")
        Set<@NotNull Interest> interests,

        Priority priority,

        Pace pace
) {
}

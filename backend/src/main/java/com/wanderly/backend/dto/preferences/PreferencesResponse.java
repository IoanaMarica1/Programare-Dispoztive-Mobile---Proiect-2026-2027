package com.wanderly.backend.dto.preferences;

import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Priority;
import com.wanderly.backend.model.TravelMode;

import java.util.Set;

public record PreferencesResponse(
        TravelMode travelMode,
        Set<Interest> interests,
        Priority priority,
        Pace pace
) {
}

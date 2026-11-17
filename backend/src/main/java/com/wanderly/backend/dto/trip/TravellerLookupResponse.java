package com.wanderly.backend.dto.trip;

import java.util.UUID;

/**
 * A Wanderly user found by email, to add to a group trip. Their preferences themselves stay private;
 * {@code hasPreferences} only tells the app whether it must ask for them by hand.
 */
public record TravellerLookupResponse(
        UUID userId,
        String name,
        String profilePicture,
        boolean hasPreferences
) {
}

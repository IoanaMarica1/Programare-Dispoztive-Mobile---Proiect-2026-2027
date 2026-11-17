package com.wanderly.backend.dto.trip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A group trip: the usual trip details plus the other travellers. The organiser (the signed-in user)
 * is always a member, with their saved preferences, so a group has 2 to {@link #MAX_PEOPLE} people.
 */
public record GenerateGroupTripRequest(
        @NotNull(message = "Trip details are required.")
        @Valid
        GenerateTripRequest trip,
        @NotNull(message = "Add at least one other traveller.")
        @Size(min = 1, max = GenerateGroupTripRequest.MAX_PEOPLE - 1,
                message = "A group has 2 to " + GenerateGroupTripRequest.MAX_PEOPLE + " people, you included.")
        List<@NotNull @Valid GroupMemberRequest> members
) {
    public static final int MAX_PEOPLE = 8;
}

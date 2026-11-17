package com.wanderly.backend.dto.trip;

import com.wanderly.backend.model.Interest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A trip. For a group trip, {@code members} lists everyone (organiser first), each stop says who it
 * suits, and {@code fairness} is the smallest share of stops suiting any one member (0 to 1).
 * For a solo trip {@code members} is empty and {@code fairness} null.
 * {@code owner} is false when a member views a group trip someone else organised: only the organiser
 * can change, save, discard or regenerate it.
 */
public record TripResponse(
        UUID id,
        String destination,
        String country,
        Double latitude,
        Double longitude,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime dayStart,
        LocalTime dayEnd,
        boolean saved,
        List<StopResponse> stops,
        List<MemberResponse> members,
        Double fairness,
        boolean owner,
        String organiserName
) {
    /** {@code suitedTo} holds member ids; {@code matchScore} is their share of the group (null when solo). */
    public record StopResponse(
            UUID id,
            int dayNumber,
            int position,
            String name,
            String category,
            double latitude,
            double longitude,
            LocalTime startTime,
            int durationMinutes,
            String description,
            String openingHours,
            List<UUID> suitedTo,
            Double matchScore
    ) {}

    /**
     * {@code userId} is null for someone entered by hand. {@code interests} are only shown for those:
     * a Wanderly user's saved preferences stay private, the stop tags already say what suits them.
     */
    public record MemberResponse(
            UUID id,
            String name,
            UUID userId,
            String profilePicture,
            Set<Interest> interests,
            int matchedStops
    ) {}
}

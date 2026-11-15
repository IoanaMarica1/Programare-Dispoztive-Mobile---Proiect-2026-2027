package com.wanderly.backend.dto.trip;

import com.wanderly.backend.model.Pace;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public record GenerateTripRequest(
        @NotBlank(message = "Destination is required.")
        @Size(max = 100, message = "Destination must not contain more than 100 characters.")
        String destination,
        @Size(max = 100, message = "Country must not contain more than 100 characters.")
        String country,
        // Optional city centre from autocomplete; saves one place lookup.
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
        Double latitude,
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
        Double longitude,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalTime dayStart,
        @NotNull LocalTime dayEnd,
        // Optional: the pace for this trip; without it the one from the saved preferences applies.
        Pace pace
) {
    public static final int MAX_DAYS = 14;
    public static final int MIN_DAY_MINUTES = 60;

    /** Number of days in the trip, both ends included. */
    public int days() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    // The checks below skip missing values; @NotNull reports those.

    @AssertTrue(message = "Start date cannot be in the past.")
    public boolean isStartDateNotPast() {
        // One day of slack: the phone's "today" is UTC, and the traveler may be in another time zone.
        return startDate == null || !startDate.isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }

    @AssertTrue(message = "End date must be on or after the start date.")
    public boolean isEndDateNotBeforeStart() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Trips can last at most " + MAX_DAYS + " days.")
    public boolean isWithinMaxDays() {
        return startDate == null || endDate == null || endDate.isBefore(startDate) || days() <= MAX_DAYS;
    }

    @AssertTrue(message = "The day must end at least one hour after it starts.")
    public boolean isDailyWindowValid() {
        return dayStart == null || dayEnd == null
                || Duration.between(dayStart, dayEnd).toMinutes() >= MIN_DAY_MINUTES;
    }
}

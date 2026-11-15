package com.wanderly.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "trip_stops")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    // Exposed to the app instead of the internal database id (swap / remove a stop).
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Getter
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private Trip trip;

    @Column(name = "day_number", nullable = false)
    @Getter
    private Integer dayNumber;

    // Position and start time change whenever the day is re-planned.
    @Column(nullable = false)
    @Getter
    @Setter
    private Integer position;

    @Column(nullable = false, length = 200)
    @Getter
    private String name;

    @Column(length = 50)
    @Getter
    private String category;

    @Column(nullable = false)
    @Getter
    private Double latitude;

    @Column(nullable = false)
    @Getter
    private Double longitude;

    @Column(name = "start_time", nullable = false)
    @Getter
    @Setter
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    @Getter
    private Integer durationMinutes;

    @Column(length = 300)
    @Getter
    private String description;

    @Column(name = "opening_hours", length = 255)
    @Getter
    private String openingHours;

    @Builder
    public TripStop(Integer dayNumber, Integer position, String name, String category,
                    Double latitude, Double longitude, LocalTime startTime, Integer durationMinutes,
                    String description, String openingHours) {
        this.dayNumber = dayNumber;
        this.position = position;
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.description = description;
        this.openingHours = openingHours;
    }
}
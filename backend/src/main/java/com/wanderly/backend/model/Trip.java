package com.wanderly.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Getter
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @Getter
    private User user;

    @Column(nullable = false, length = 100)
    @Getter
    private String destination;

    @Column(length = 100)
    @Getter
    private String country;

    @Column(nullable = false)
    @Getter
    private Double latitude;

    @Column(nullable = false)
    @Getter
    private Double longitude;

    @Column(name = "start_date", nullable = false)
    @Getter
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    @Getter
    private LocalDate endDate;

    @Column(name = "day_start", nullable = false)
    @Getter
    private LocalTime dayStart;

    @Column(name = "day_end", nullable = false)
    @Getter
    private LocalTime dayEnd;

    @Column(name = "created_at", nullable = false)
    @Getter
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC, position ASC")
    @Getter
    private List<TripStop> stops = new ArrayList<>();

    @Builder
    public Trip(User user, String destination, String country, Double latitude, Double longitude,
                LocalDate startDate, LocalDate endDate, LocalTime dayStart, LocalTime dayEnd) {
        this.user = user;
        this.destination = destination;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
    }

    public void addStop(TripStop stop) {
        stop.setTrip(this);
        stops.add(stop);
    }

    public void setCenter(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
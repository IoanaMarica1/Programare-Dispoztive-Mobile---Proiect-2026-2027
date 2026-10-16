package com.wanderly.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visits", uniqueConstraints = @UniqueConstraint(
        name = "uk_visit_user_objective",
        columnNames = {"user_id", "objective_name", "city", "country"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    // Exposed to the app instead of the internal database id.
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Getter
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @Getter
    private User user;

    @Column(name = "objective_name", nullable = false, length = 200)
    @Getter
    private String objectiveName;

    @Column(nullable = false, length = 100)
    @Getter
    private String city;

    @Column(nullable = false, length = 100)
    @Getter
    private String country;

    @Column(nullable = false)
    @Getter
    private Double latitude;

    @Column(nullable = false)
    @Getter
    private Double longitude;

    @Column(name = "visited_at", nullable = false)
    @Getter
    private Instant visitedAt;

    @Builder
    public Visit(User user, String objectiveName, String city, String country,
                 Double latitude, Double longitude, Instant visitedAt) {
        this.user = user;
        this.objectiveName = objectiveName;
        this.city = city;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.visitedAt = visitedAt;
    }
}

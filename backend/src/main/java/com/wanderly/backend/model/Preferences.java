package com.wanderly.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Preferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    // Exposed to the app instead of the internal database id.
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Getter
    private UUID publicId = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @Getter
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false)
    @Getter
    @Setter
    private TravelMode travelMode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_interests", joinColumns = @JoinColumn(name = "preferences_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "interest", nullable = false)
    @Getter
    @Setter
    private Set<Interest> interests = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private Pace pace;

    @Builder
    public Preferences(User user, TravelMode travelMode, Set<Interest> interests, Priority priority, Pace pace) {
        this.user = user;
        this.travelMode = travelMode;
        if (interests != null) {
            this.interests = new HashSet<>(interests);
        }
        this.priority = priority;
        this.pace = pace;
    }
}

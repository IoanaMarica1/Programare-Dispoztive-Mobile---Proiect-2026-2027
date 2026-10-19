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

    // Every wizard question accepts several answers; travel modes and interests need at least one.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_travel_modes", joinColumns = @JoinColumn(name = "preferences_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false)
    @Getter
    @Setter
    private Set<TravelMode> travelModes = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_interests", joinColumns = @JoinColumn(name = "preferences_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "interest", nullable = false)
    @Getter
    @Setter
    private Set<Interest> interests = new HashSet<>();

    // Optional wizard step: empty when skipped.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_priorities", joinColumns = @JoinColumn(name = "preferences_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Getter
    @Setter
    private Set<Priority> priorities = new HashSet<>();

    // Optional wizard step: empty when skipped.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_paces", joinColumns = @JoinColumn(name = "preferences_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "pace", nullable = false)
    @Getter
    @Setter
    private Set<Pace> paces = new HashSet<>();

    @Builder
    public Preferences(User user, Set<TravelMode> travelModes, Set<Interest> interests,
                       Set<Priority> priorities, Set<Pace> paces) {
        this.user = user;
        if (travelModes != null) {
            this.travelModes = new HashSet<>(travelModes);
        }
        if (interests != null) {
            this.interests = new HashSet<>(interests);
        }
        if (priorities != null) {
            this.priorities = new HashSet<>(priorities);
        }
        if (paces != null) {
            this.paces = new HashSet<>(paces);
        }
    }
}

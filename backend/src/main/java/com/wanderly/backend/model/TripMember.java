package com.wanderly.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One person on a group trip, with a copy of their preferences taken when the plan was generated.
 * {@code user} is set when the member has a Wanderly account, and null when the organiser entered
 * their preferences by hand.
 */
@Entity
@Table(name = "trip_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Getter
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Getter
    private User user;

    @Column(nullable = false, length = 100)
    @Getter
    private String name;

    /** 1 is the organiser; the others follow in the order they were added. */
    @Column(nullable = false)
    @Getter
    private Integer position;

    /** The member left the trip: it no longer shows for them. Nothing else changes. */
    @Column(nullable = false)
    @Getter
    private boolean hidden = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_member_interests", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "interest", nullable = false)
    @Getter
    private Set<Interest> interests = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_member_priorities", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Getter
    private Set<Priority> priorities = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_member_paces", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "pace", nullable = false)
    @Getter
    private Set<Pace> paces = new HashSet<>();

    @Builder
    public TripMember(User user, String name, Integer position, Set<Interest> interests,
                      Set<Priority> priorities, Set<Pace> paces) {
        this.user = user;
        this.name = name;
        this.position = position;
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

    public void hide() {
        hidden = true;
    }
}

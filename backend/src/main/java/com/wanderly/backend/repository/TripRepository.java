package com.wanderly.backend.repository;

import com.wanderly.backend.model.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // Only the owner's trips; another traveler's id looks like a missing trip. Stops come along,
    // so callers can use them outside a transaction. Everything that changes a trip goes through this.
    @EntityGraph(attributePaths = "stops")
    Optional<Trip> findByPublicIdAndUserEmail(UUID publicId, String email);

    boolean existsByPublicIdAndUserEmail(UUID publicId, String email);

    /**
     * A saved group trip, for one of its members (read-only). Drafts, trips the member left and trips
     * they aren't on all look missing.
     */
    @EntityGraph(attributePaths = "stops")
    @Query("""
            SELECT t FROM Trip t
            WHERE t.publicId = :publicId AND t.saved = true
              AND EXISTS (SELECT 1 FROM TripMember m
                          WHERE m.trip = t AND m.user.email = :email AND m.hidden = false)
            """)
    Optional<Trip> findSavedForMember(@Param("publicId") UUID publicId, @Param("email") String email);

    /** Saved trips the user organised, plus saved group trips they're on and haven't left. */
    @EntityGraph(attributePaths = "user")
    @Query(value = """
            SELECT t FROM Trip t
            WHERE t.saved = true
              AND (t.user.email = :email
                   OR EXISTS (SELECT 1 FROM TripMember m
                              WHERE m.trip = t AND m.user.email = :email AND m.hidden = false))
            """,
            countQuery = """
            SELECT COUNT(t) FROM Trip t
            WHERE t.saved = true
              AND (t.user.email = :email
                   OR EXISTS (SELECT 1 FROM TripMember m
                              WHERE m.trip = t AND m.user.email = :email AND m.hidden = false))
            """)
    Page<Trip> findSavedVisibleTo(@Param("email") String email, Pageable pageable);
}

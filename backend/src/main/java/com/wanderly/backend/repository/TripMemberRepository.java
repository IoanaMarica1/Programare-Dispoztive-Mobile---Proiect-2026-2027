package com.wanderly.backend.repository;

import com.wanderly.backend.model.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    // Their preferences are fetched eagerly, so the result can be used outside a transaction.
    List<TripMember> findByTripIdOrderByPositionAsc(Long tripId);

    /** The user's place on a saved group trip someone else organised, unless they already left it. */
    @Query("""
            SELECT m FROM TripMember m
            WHERE m.trip.publicId = :tripId AND m.trip.saved = true
              AND m.user.email = :email AND m.hidden = false
              AND m.trip.user.email <> :email
            """)
    Optional<TripMember> findMembership(@Param("tripId") UUID tripId, @Param("email") String email);
}

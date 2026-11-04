package com.wanderly.backend.repository;

import com.wanderly.backend.model.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // Only the owner's trips; another traveler's id looks like a missing trip. Stops come along,
    // so callers can use them outside a transaction.
    @EntityGraph(attributePaths = "stops")
    Optional<Trip> findByPublicIdAndUserEmail(UUID publicId, String email);

    Page<Trip> findByUserEmailAndSavedTrue(String email, Pageable pageable);
}

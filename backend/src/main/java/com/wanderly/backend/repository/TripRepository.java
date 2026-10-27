package com.wanderly.backend.repository;

import com.wanderly.backend.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByPublicId(UUID publicId);
    List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId);
}
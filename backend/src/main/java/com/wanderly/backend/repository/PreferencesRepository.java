package com.wanderly.backend.repository;

import com.wanderly.backend.model.Preferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferencesRepository extends JpaRepository<Preferences, Long> {

    Optional<Preferences> findByUserId(Long userId);
}

package com.wanderly.backend.repository;

import com.wanderly.backend.dto.history.CountryHistoryResponse;
import com.wanderly.backend.model.Visit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    Optional<Visit> findByUserIdAndObjectiveNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(
            Long userId, String objectiveName, String city, String country);

    @Query(value = """
            SELECT new com.wanderly.backend.dto.history.CountryHistoryResponse(
                v.country, COUNT(v), MAX(v.visitedAt))
            FROM Visit v
            WHERE v.user.id = :userId
            GROUP BY v.country
            ORDER BY MAX(v.visitedAt) DESC
            """,
            countQuery = "SELECT COUNT(DISTINCT v.country) FROM Visit v WHERE v.user.id = :userId")
    Page<CountryHistoryResponse> findCountriesByUserId(@Param("userId") Long userId, Pageable pageable);

    Page<Visit> findByUserIdAndCountryIgnoreCaseOrderByVisitedAtDesc(Long userId, String country, Pageable pageable);
}

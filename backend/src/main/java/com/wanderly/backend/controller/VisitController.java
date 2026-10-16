package com.wanderly.backend.controller;

import com.wanderly.backend.dto.common.PageResponse;
import com.wanderly.backend.dto.history.CountryHistoryResponse;
import com.wanderly.backend.dto.visit.VisitRequest;
import com.wanderly.backend.dto.visit.VisitResponse;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.service.VisitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class VisitController {

    private static final int MAX_PAGE_SIZE = 50;

    private final VisitService visitService;

    // 201 on a first visit (points awarded), 200 when the objective was already visited.
    @PostMapping("/visits")
    public ResponseEntity<VisitResultResponse> recordVisit(Authentication authentication,
                                                           @Valid @RequestBody VisitRequest request) {
        VisitResultResponse result = visitService.recordVisit(authentication.getName(), request);
        return ResponseEntity.status(result.firstVisit() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    @GetMapping("/history/countries")
    public PageResponse<CountryHistoryResponse> getVisitedCountries(Authentication authentication,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return visitService.getVisitedCountries(authentication.getName(), pageable(page, size));
    }

    @GetMapping("/history/countries/{country}")
    public PageResponse<VisitResponse> getVisitsInCountry(Authentication authentication,
                                                          @PathVariable String country,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return visitService.getVisitsInCountry(authentication.getName(), country, pageable(page, size));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}

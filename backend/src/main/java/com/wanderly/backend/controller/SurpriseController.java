package com.wanderly.backend.controller;

import com.wanderly.backend.dto.surprise.CheckInRequest;
import com.wanderly.backend.dto.surprise.SurprisePlaceResponse;
import com.wanderly.backend.dto.surprise.SurpriseRequest;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.service.SurpriseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/surprise")
@RequiredArgsConstructor
public class SurpriseController {

    private final SurpriseService surpriseService;

    // POST because of the list of places to skip. 404 when nothing open fits the mood, 503 when the map is busy.
    @PostMapping
    public SurprisePlaceResponse suggest(@Valid @RequestBody SurpriseRequest request) {
        return surpriseService.suggest(request);
    }

    // 201 on a first visit (points awarded), 200 when the place was already visited.
    @PostMapping("/check-in")
    public ResponseEntity<VisitResultResponse> checkIn(Authentication authentication,
                                                       @Valid @RequestBody CheckInRequest request) {
        VisitResultResponse result = surpriseService.checkIn(authentication.getName(), request);
        return ResponseEntity.status(result.firstVisit() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}

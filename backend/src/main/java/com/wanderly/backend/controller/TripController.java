package com.wanderly.backend.controller;

import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping("/generate")
    public TripResponse generate(Authentication authentication,
                                 @Valid @RequestBody GenerateTripRequest request) {
        return tripService.generate(authentication.getName(), request);
    }
}
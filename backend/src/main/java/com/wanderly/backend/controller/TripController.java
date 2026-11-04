package com.wanderly.backend.controller;

import com.wanderly.backend.dto.common.PageResponse;
import com.wanderly.backend.dto.trip.CandidateResponse;
import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.StopRequest;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.dto.trip.TripSummaryResponse;
import com.wanderly.backend.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private static final int MAX_PAGE_SIZE = 50;

    private final TripService tripService;

    @PostMapping("/generate")
    public TripResponse generate(Authentication authentication,
                                 @Valid @RequestBody GenerateTripRequest request) {
        return tripService.generate(authentication.getName(), request);
    }

    // Saved plans only, newest first. A generated plan is a draft until it is saved.
    @GetMapping
    public PageResponse<TripSummaryResponse> savedTrips(Authentication authentication,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return tripService.savedTrips(authentication.getName(), PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping("/{tripId}/save")
    public TripResponse save(Authentication authentication, @PathVariable UUID tripId) {
        return tripService.save(authentication.getName(), tripId);
    }

    // Drafts only; 409 for a saved plan.
    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(Authentication authentication, @PathVariable UUID tripId) {
        tripService.deleteDraft(authentication.getName(), tripId);
    }

    @GetMapping("/{tripId}")
    public TripResponse getTrip(Authentication authentication, @PathVariable UUID tripId) {
        return tripService.getTrip(authentication.getName(), tripId);
    }

    // Places to add or swap in, nearest to that day's stops first.
    @GetMapping("/{tripId}/candidates")
    public List<CandidateResponse> candidates(Authentication authentication, @PathVariable UUID tripId,
                                              @RequestParam(required = false) Integer day) {
        return tripService.candidates(authentication.getName(), tripId, day);
    }

    // Each edit re-plans the day (order and times) and returns the whole trip.
    @PostMapping("/{tripId}/stops")
    public TripResponse addStop(Authentication authentication, @PathVariable UUID tripId,
                                @Valid @RequestBody StopRequest request) {
        return tripService.addStop(authentication.getName(), tripId, request);
    }

    @PutMapping("/{tripId}/stops/{stopId}")
    public TripResponse replaceStop(Authentication authentication, @PathVariable UUID tripId,
                                    @PathVariable UUID stopId, @Valid @RequestBody StopRequest request) {
        return tripService.replaceStop(authentication.getName(), tripId, stopId, request);
    }

    @DeleteMapping("/{tripId}/stops/{stopId}")
    public TripResponse removeStop(Authentication authentication, @PathVariable UUID tripId,
                                   @PathVariable UUID stopId) {
        return tripService.removeStop(authentication.getName(), tripId, stopId);
    }
}

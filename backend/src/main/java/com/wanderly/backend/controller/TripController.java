package com.wanderly.backend.controller;

import com.wanderly.backend.dto.common.PageResponse;
import com.wanderly.backend.dto.trip.CandidateResponse;
import com.wanderly.backend.dto.trip.GenerateGroupTripRequest;
import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.RegenerateTripRequest;
import com.wanderly.backend.dto.trip.StopRequest;
import com.wanderly.backend.dto.trip.TravellerLookupResponse;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.dto.trip.TripSummaryResponse;
import com.wanderly.backend.service.GroupTripService;
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
    private final GroupTripService groupTripService;

    @PostMapping("/generate")
    public TripResponse generate(Authentication authentication,
                                 @Valid @RequestBody GenerateTripRequest request) {
        return tripService.generate(authentication.getName(), request);
    }

    // ---- group trips (story D1)

    // Is this friend on Wanderly? 404 means: enter their preferences by hand.
    @GetMapping("/group/lookup")
    public TravellerLookupResponse lookupTraveller(Authentication authentication, @RequestParam String email) {
        return groupTripService.lookup(authentication.getName(), email);
    }

    @PostMapping("/generate-group")
    public TripResponse generateGroup(Authentication authentication,
                                      @Valid @RequestBody GenerateGroupTripRequest request) {
        return groupTripService.generate(authentication.getName(), request);
    }

    // A new plan for the same trip and people (solo or group). Replaces the old one if it was a draft.
    @PostMapping("/{tripId}/regenerate")
    public TripResponse regenerate(Authentication authentication, @PathVariable UUID tripId,
                                   @RequestBody(required = false) RegenerateTripRequest request) {
        return groupTripService.regenerate(authentication.getName(), tripId,
                request == null ? null : request.pace());
    }

    // A member removes a saved group trip from their own list; nothing changes for the others.
    @DeleteMapping("/{tripId}/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Authentication authentication, @PathVariable UUID tripId) {
        groupTripService.leave(authentication.getName(), tripId);
    }

    // Saved plans only (own ones and group trips the user is on), newest first. A generated plan is a draft until it is saved.
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

    // Group members may read a saved trip; every other /{tripId} endpoint is for the organiser only.
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

package com.wanderly.backend.controller;

import com.wanderly.backend.dto.preferences.PreferencesRequest;
import com.wanderly.backend.dto.preferences.PreferencesResponse;
import com.wanderly.backend.service.PreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/preferences")
@RequiredArgsConstructor
public class PreferencesController {

    private final PreferencesService preferencesService;

    // 204 No Content tells the app to show the "complete the wizard" empty state.
    @GetMapping
    public ResponseEntity<PreferencesResponse> getPreferences(Authentication authentication) {
        return preferencesService.getPreferences(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public PreferencesResponse savePreferences(Authentication authentication,
                                               @Valid @RequestBody PreferencesRequest request) {
        return preferencesService.savePreferences(authentication.getName(), request);
    }
}

package com.wanderly.backend.service;

import com.wanderly.backend.dto.preferences.PreferencesRequest;
import com.wanderly.backend.dto.preferences.PreferencesResponse;
import com.wanderly.backend.model.Preferences;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.PreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PreferencesService {

    private final PreferencesRepository preferencesRepository;
    private final UserService userService;

    // Empty when the traveler has not completed the wizard yet.
    @Transactional(readOnly = true)
    public Optional<PreferencesResponse> getPreferences(String email) {
        User user = userService.getByEmail(email);
        return preferencesRepository.findByUserId(user.getId()).map(this::toResponse);
    }

    @Transactional
    public PreferencesResponse savePreferences(String email, PreferencesRequest request) {
        User user = userService.getByEmail(email);
        Preferences preferences = preferencesRepository.findByUserId(user.getId())
                .orElseGet(() -> Preferences.builder().user(user).build());

        preferences.setTravelModes(new HashSet<>(request.travelModes()));
        preferences.setInterests(new HashSet<>(request.interests()));
        preferences.setPriorities(copyOrEmpty(request.priorities()));
        preferences.setPaces(copyOrEmpty(request.paces()));

        return toResponse(preferencesRepository.save(preferences));
    }

    private PreferencesResponse toResponse(Preferences preferences) {
        return new PreferencesResponse(
                Set.copyOf(preferences.getTravelModes()),
                Set.copyOf(preferences.getInterests()),
                Set.copyOf(preferences.getPriorities()),
                Set.copyOf(preferences.getPaces())
        );
    }

    // A skipped optional step may arrive as null.
    private static <T> Set<T> copyOrEmpty(Set<T> values) {
        return values == null ? new HashSet<>() : new HashSet<>(values);
    }
}

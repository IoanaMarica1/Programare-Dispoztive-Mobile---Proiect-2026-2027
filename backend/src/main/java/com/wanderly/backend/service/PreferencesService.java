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

        preferences.setTravelMode(request.travelMode());
        preferences.setInterests(new HashSet<>(request.interests()));
        preferences.setPriority(request.priority());
        preferences.setPace(request.pace());

        return toResponse(preferencesRepository.save(preferences));
    }

    private PreferencesResponse toResponse(Preferences preferences) {
        return new PreferencesResponse(
                preferences.getTravelMode(),
                Set.copyOf(preferences.getInterests()),
                preferences.getPriority(),
                preferences.getPace()
        );
    }
}

package com.wanderly.backend.service;

import com.wanderly.backend.dto.user.UserResponse;
import com.wanderly.backend.dto.user.UserUpdateRequest;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        return toResponse(getByEmail(email));
    }

    @Transactional
    public UserResponse updateProfile(String email, UserUpdateRequest request) {
        User user = getByEmail(email);
        user.setName(request.name().trim());
        user.setBio(request.bio() == null || request.bio().isBlank() ? null : request.bio().trim());
        user.setAddress(request.address().trim());
        return toResponse(user);
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getName(),
                user.getProfilePicture(),
                user.getBio(),
                user.getAddress(),
                user.getAge(),
                user.getPoints()
        );
    }
}

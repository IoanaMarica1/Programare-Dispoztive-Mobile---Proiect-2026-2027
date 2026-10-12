package com.wanderly.backend.dto.auth;

import com.wanderly.backend.dto.user.UserResponse;

public record AuthenticationResponse(
        String token,
        UserResponse user
) {
}
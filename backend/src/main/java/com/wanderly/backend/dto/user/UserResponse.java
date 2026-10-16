package com.wanderly.backend.dto.user;

import java.util.UUID;

public record UserResponse(
        UUID publicId,
        String email,
        String name,
        String profilePicture,
        String bio,
        String address,
        Integer age,
        Integer points
) {
}

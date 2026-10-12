package com.wanderly.backend.dto.user;

public record UserResponse(
        Long id,
        String email,
        String name,
        String profilePicture,
        String address,
        Integer age
) {
}

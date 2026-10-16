package com.wanderly.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(

        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name has length of maximum 100.")
        String name,

        @Size(max = 500, message = "Bio must not contain more than 500 characters.")
        String bio,

        @NotBlank(message = "Home city is required.")
        @Size(max = 50, message = "Home city must not contain more than 50 characters.")
        String address
) {
}

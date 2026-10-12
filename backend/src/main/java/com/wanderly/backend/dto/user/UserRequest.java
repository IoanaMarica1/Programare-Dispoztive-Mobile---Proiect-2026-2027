package com.wanderly.backend.dto.user;

import jakarta.validation.constraints.*;

public record UserRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name has length of maximum 100.")
        String name,

        @Size(max = 500)
        String profilePicture,

        @NotBlank(message = "Address is required.")
        @Size(max = 50, message = "Addres must not contain more than 50 characters.")
        String address,

        @NotNull(message = "Age is required.")
        @Min(value = 18, message = "Age must be greater than 18.")
        @Max(value = 100, message = "Invalid age.")
        Integer age
) {
}
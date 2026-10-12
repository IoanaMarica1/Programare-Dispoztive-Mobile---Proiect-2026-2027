package com.wanderly.backend.dto.auth;

import jakarta.validation.constraints.*;

public record RegisterRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Email is not valid.")
        @Size(max = 255, message = "Email is too long.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must have between 8 and 72 characters.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit.")
        String password,

        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name has length of maximum 100.")
        String name,

        @NotBlank(message = "Address is required.")
        @Size(max = 50, message = "Address must not contain more than 50 characters.")
        String address,

        @NotNull(message = "Age is required.")
        @Min(value = 18, message = "You must be at least 18 years old.")
        @Max(value = 100, message = "Invalid age.")
        Integer age
) {
}
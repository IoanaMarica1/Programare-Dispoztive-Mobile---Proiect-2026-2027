package com.wanderly.backend.controller;

import com.wanderly.backend.dto.user.UserResponse;
import com.wanderly.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public UserResponse getProfile(Authentication authentication) {
        return userService.getProfile(authentication.getName());
    }
}
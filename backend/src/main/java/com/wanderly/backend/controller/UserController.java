package com.wanderly.backend.controller;

import com.wanderly.backend.dto.user.UserResponse;
import com.wanderly.backend.dto.user.UserUpdateRequest;
import com.wanderly.backend.service.AvatarService;
import com.wanderly.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

    @GetMapping("/profile")
    public UserResponse getProfile(Authentication authentication) {
        return userService.getProfile(authentication.getName());
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(Authentication authentication,
                                      @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(Authentication authentication,
                                     @RequestParam("file") MultipartFile file) {
        return avatarService.uploadAvatar(authentication.getName(), file);
    }
}

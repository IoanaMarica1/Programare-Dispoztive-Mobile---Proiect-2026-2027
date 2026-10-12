package com.wanderly.backend.auth;

import com.wanderly.backend.dto.auth.AuthenticationResponse;
import com.wanderly.backend.dto.auth.LoginRequest;
import com.wanderly.backend.dto.auth.RegisterRequest;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.UserRepository;
import com.wanderly.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        String email = request.email();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name().trim())
                .address(request.address().trim())
                .age(request.age())
                .build();

        userRepository.save(user);
        return buildResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        String email = request.email();

        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Incorrect email or password."));

        return buildResponse(user);
    }

    private AuthenticationResponse buildResponse(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthenticationResponse(token, userService.toResponse(user));
    }

}
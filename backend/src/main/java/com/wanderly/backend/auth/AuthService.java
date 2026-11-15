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

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** BCrypt only hashes the first 72 bytes, and Spring Security rejects longer passwords outright. */
    public static final int MAX_PASSWORD_BYTES = 72;

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

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
        String email = normalizeEmail(request.email());
        // No account can have a longer password, and BCrypt would throw on it.
        boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;

        User user = userRepository.findByEmail(email)
                .filter(u -> !tooLong && passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Incorrect email or password."));

        return buildResponse(user);
    }

    // Emails are stored lower-case, so "Ana@Mail.com" and "ana@mail.com" are one account.
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthenticationResponse buildResponse(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthenticationResponse(token, userService.toResponse(user));
    }

}
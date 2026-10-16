package com.wanderly.backend.auth;

import com.wanderly.backend.dto.auth.AuthenticationResponse;
import com.wanderly.backend.dto.auth.LoginRequest;
import com.wanderly.backend.dto.auth.RegisterRequest;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.UserRepository;
import com.wanderly.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-0123456789";

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtService = new JwtService(SECRET, 3_600_000L);
        UserService userService = new UserService(userRepository);
        authService = new AuthService(userRepository, userService, passwordEncoder, jwtService);
    }


    @Test
    @DisplayName("Register: salvează userul cu parola criptată și returnează token")
    void register_newEmail_savesUserAndReturnsToken() {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        RegisterRequest request = new RegisterRequest("ana@test.com", "parola123", "Ana", "Cluj", 26);
        AuthenticationResponse response = authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();

        assertThat(user.getEmail()).isEqualTo("ana@test.com");
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getAddress()).isEqualTo("Cluj");
        assertThat(user.getAge()).isEqualTo(26);

        assertThat(user.getPasswordHash()).isNotEqualTo("parola123").startsWith("$2");
        assertThat(passwordEncoder.matches("parola123", user.getPasswordHash())).isTrue();

        assertThat(jwtService.parseToken(response.token()).getSubject()).isEqualTo("ana@test.com");
        assertThat(response.user().email()).isEqualTo("ana@test.com");
        assertThat(response.user().publicId()).isEqualTo(user.getPublicId());
    }

    @Test
    @DisplayName("Register: email deja folosit → 409 Conflict, nu se salvează nimic")
    void register_existingEmail_throwsConflict() {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("ana@test.com", "parola123", "Ana", "Cluj", 26);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.register(request));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    // ---------- LOGIN ----------

    @Test
    @DisplayName("Login: parolă corectă → token valid")
    void login_correctPassword_returnsToken() {
        User user = existingUser("parola123");
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        AuthenticationResponse response = authService.login(new LoginRequest("ana@test.com", "parola123"));

        assertThat(jwtService.parseToken(response.token()).getSubject()).isEqualTo("ana@test.com");
        assertThat(response.user().name()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Login: parolă greșită → 401")
    void login_wrongPassword_throwsUnauthorized() {
        User user = existingUser("parola123");
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("ana@test.com", "gresita99")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getReason()).isEqualTo("Incorrect email or password.");
    }

    @Test
    @DisplayName("Login: email inexistent → 401 cu ACELAȘI mesaj ca la parolă greșită")
    void login_unknownEmail_throwsUnauthorizedWithSameMessage() {
        when(userRepository.findByEmail("nimeni@test.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("nimeni@test.com", "parola123")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getReason()).isEqualTo("Incorrect email or password.");
    }

    // ---------- helper ----------

    private User existingUser(String rawPassword) {
        User user = User.builder()
                .email("ana@test.com")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .name("Ana")
                .address("Cluj")
                .age(26)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
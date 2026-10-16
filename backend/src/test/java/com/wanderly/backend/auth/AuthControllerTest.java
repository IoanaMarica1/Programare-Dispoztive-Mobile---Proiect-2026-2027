package com.wanderly.backend.auth;

import com.wanderly.backend.GlobalExceptionHandler;
import com.wanderly.backend.dto.auth.AuthenticationResponse;
import com.wanderly.backend.dto.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /register cu date valide → 201 + token")
    void register_validBody_returns201() throws Exception {
        UserResponse user = new UserResponse(UUID.randomUUID(), "ana@test.com", "Ana", null, null, "Cluj", 26, 0);
        when(authService.register(any())).thenReturn(new AuthenticationResponse("fake-token", user));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ana@test.com",
                                  "password": "parola123",
                                  "name": "Ana",
                                  "address": "Cluj",
                                  "age": 26
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("fake-token"))
                .andExpect(jsonPath("$.user.email").value("ana@test.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /register cu date invalide → 400 cu eroare pentru fiecare câmp")
    void register_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "abc",
                                  "password": "123",
                                  "name": "",
                                  "address": "Cluj",
                                  "age": 15
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.age").exists());

        verifyNoInteractions(authService); // datele invalide nu ajung în service
    }

    @Test
    @DisplayName("POST /register cu email existent → 409")
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists."));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "ana@test.com", "password": "parola123",
                                  "name": "Ana", "address": "Cluj", "age": 26 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    @DisplayName("POST /login cu parolă greșită → 401")
    void login_wrongCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "ana@test.com", "password": "gresita99" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }

    @Test
    @DisplayName("POST /login fără email → 400")
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "password": "parola123" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        verifyNoInteractions(authService);
    }
}
package com.wanderly.backend.service;

import com.wanderly.backend.dto.user.UserResponse;
import com.wanderly.backend.dto.user.UserUpdateRequest;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("Update profile: modifică name, bio, home city și păstrează age, email, points")
    void updateProfile_updatesEditableFieldsOnly() {
        User user = existingUser();
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        UserResponse response = userService.updateProfile("ana@test.com",
                new UserUpdateRequest("  Ana Maria ", " Iubesc istoria ", " Brasov "));

        assertThat(user.getName()).isEqualTo("Ana Maria");
        assertThat(user.getBio()).isEqualTo("Iubesc istoria");
        assertThat(user.getAddress()).isEqualTo("Brasov");
        assertThat(user.getAge()).isEqualTo(26);
        assertThat(user.getPoints()).isEqualTo(40);
        assertThat(response.email()).isEqualTo("ana@test.com");
        assertThat(response.points()).isEqualTo(40);
        assertThat(response.bio()).isEqualTo("Iubesc istoria");
    }

    @Test
    @DisplayName("Update profile: bio gol → se salvează null")
    void updateProfile_blankBio_clearsBio() {
        User user = existingUser();
        user.setBio("bio vechi");
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        userService.updateProfile("ana@test.com", new UserUpdateRequest("Ana", "   ", "Cluj"));

        assertThat(user.getBio()).isNull();
    }

    @Test
    @DisplayName("Update profile: user inexistent → 404")
    void updateProfile_unknownUser_throwsNotFound() {
        when(userRepository.findByEmail("nimeni@test.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.updateProfile("nimeni@test.com", new UserUpdateRequest("Ana", null, "Cluj")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Validare: name gol, bio prea lung și home city gol sunt respinse")
    void updateRequest_invalidFields_failValidation() {
        Set<ConstraintViolation<UserUpdateRequest>> violations =
                VALIDATOR.validate(new UserUpdateRequest(" ", "a".repeat(501), ""));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "bio", "address");
    }

    @Test
    @DisplayName("Validare: request corect fără bio trece")
    void updateRequest_validWithoutBio_passesValidation() {
        assertThat(VALIDATOR.validate(new UserUpdateRequest("Ana", null, "Cluj"))).isEmpty();
    }

    private User existingUser() {
        User user = User.builder()
                .email("ana@test.com")
                .passwordHash("hash")
                .name("Ana")
                .address("Cluj")
                .age(26)
                .build();
        user.setPoints(40);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}

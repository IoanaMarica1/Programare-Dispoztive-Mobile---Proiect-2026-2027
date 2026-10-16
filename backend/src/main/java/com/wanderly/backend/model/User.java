package com.wanderly.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    // Exposed to the app instead of the internal database id.
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Getter
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    @Getter
    private String email;

    @Column(name = "password_hash", nullable = false)
    @Getter
    private String passwordHash;

    @Column(nullable = false, length = 100)
    @Getter
    @Setter
    private String name;

    @Column(name = "profile_picture")
    @Getter
    @Setter
    private String profilePicture;

    @Column(length = 500)
    @Getter
    @Setter
    private String bio;

    // Used as the traveler's home city.
    @Column(nullable = false, length = 50)
    @Getter
    @Setter
    private String address;

    @Column(nullable = false)
    @Getter
    private Integer age;

    @Column(nullable = false)
    @Getter
    @Setter
    private Integer points = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Getter
    private Role role = Role.USER;

    @Builder
    public User(String email, String passwordHash, String name, String address, Integer age) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.address = address;
        this.age = age;
    }
}

package com.wanderly.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-0123456789";
    private static final long ONE_HOUR = 3_600_000L;

    private final JwtService jwtService = new JwtService(SECRET, ONE_HOUR);

    @Test
    @DisplayName("Token-ul generat conține email-ul și rolul")
    void generateToken_containsEmailAndRole() {
        String token = jwtService.generateToken("ana@test.com", "USER");

        Claims claims = jwtService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("ana@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration()).isInTheFuture();
    }

    @Test
    @DisplayName("Sesiune expirată: token-ul expirat este respins")
    void parseToken_expiredToken_throwsExpiredJwtException() {
        // Expirare negativă = token-ul e deja expirat în momentul creării
        JwtService expiredService = new JwtService(SECRET, -1_000L);
        String expiredToken = expiredService.generateToken("ana@test.com", "USER");

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseToken(expiredToken));
    }

    @Test
    @DisplayName("Token semnat cu altă cheie este respins")
    void parseToken_tokenSignedWithOtherKey_throws() {
        JwtService attacker = new JwtService("another-secret-key-that-is-also-long-enough-0123456789", ONE_HOUR);
        String forgedToken = attacker.generateToken("ana@test.com", "ADMIN");

        assertThrows(JwtException.class, () -> jwtService.parseToken(forgedToken));
    }

    @Test
    @DisplayName("Text care nu e token este respins")
    void parseToken_garbage_throws() {
        assertThrows(JwtException.class, () -> jwtService.parseToken("not-a-real-token"));
    }

    @Test
    @DisplayName("Cheie lipsă, prea scurtă sau lăsată ca în exemplu → aplicația nu pornește")
    void constructor_missingOrWeakSecret_throws() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", ONE_HOUR));
        assertThrows(IllegalStateException.class, () -> new JwtService("", ONE_HOUR));
        assertThrows(IllegalStateException.class,
                () -> new JwtService("CHANGE_ME_TO_A_RANDOM_STRING_OF_32_PLUS_CHARACTERS", ONE_HOUR));
    }
}
package com.wanderly.backend.auth;

import com.wanderly.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService, userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Fără header Authorization: request-ul continuă neautentificat")
    void noHeader_requestContinuesWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/profile");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull(); // filtrul a lăsat request-ul să treacă
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token valid: userul este autentificat cu email-ul și rolul din token")
    void validToken_setsAuthentication() throws Exception {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(true);
        String token = jwtService.generateToken("ana@test.com", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/profile");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("ana@test.com");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Cont șters: token valid, dar userul nu mai există → NU este autentificat")
    void validTokenForDeletedUser_doesNotAuthenticate() throws Exception {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        String token = jwtService.generateToken("ana@test.com", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/profile");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // Spring Security va răspunde apoi cu 401
    }

    @Test
    @DisplayName("Sesiune expirată: token expirat → userul NU este autentificat")
    void expiredToken_doesNotAuthenticate() throws Exception {
        JwtService expiredService = new JwtService(SECRET, -1_000L);
        String expiredToken = expiredService.generateToken("ana@test.com", "USER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/profile");
        request.addHeader("Authorization", "Bearer " + expiredToken);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // Spring Security va răspunde apoi cu 401
    }

    @Test
    @DisplayName("Token invalid: userul NU este autentificat")
    void invalidToken_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/profile");
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
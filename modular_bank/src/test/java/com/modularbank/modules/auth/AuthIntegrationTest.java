package com.modularbank.modules.auth;

import com.modularbank.shared.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerAndLoginSuccessfully() {
        var registerBody = Map.of(
            "email", "test@example.com",
            "password", "Password123!",
            "name", "Test User"
        );
        ResponseEntity<Map> registerResponse = rest.postForEntity("/auth/register", registerBody, Map.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).containsKey("accessToken");
        assertThat(registerResponse.getBody()).containsKey("refreshToken");

        // Also verify login works with same credentials
        var loginBody = Map.of("email", "test@example.com", "password", "Password123!");
        ResponseEntity<Map> loginResponse = rest.postForEntity("/auth/login", loginBody, Map.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).containsKey("accessToken");
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        rest.postForEntity("/auth/register",
            Map.of("email", "user@example.com", "password", "Password123!", "name", "User"),
            Map.class);

        var body = Map.of("email", "user@example.com", "password", "WrongPassword!");
        ResponseEntity<Map> response = rest.postForEntity("/auth/login", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshTokenReturnsNewAccessToken() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "refresh-" + suffix + "@example.com";

        rest.postForEntity("/auth/register",
            Map.of("email", email, "password", "Password123!", "name", "Refresh"), Map.class);
        Map loginBody = rest.postForEntity("/auth/login",
            Map.of("email", email, "password", "Password123!"), Map.class).getBody();
        String refreshToken = (String) loginBody.get("refreshToken");

        ResponseEntity<Map> response = rest.postForEntity(
            "/auth/refresh", Map.of("refreshToken", refreshToken), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
        assertThat(response.getBody()).containsKey("refreshToken");
        // Refresh token is rotated — new one must differ from the old one
        assertThat(response.getBody().get("refreshToken")).isNotEqualTo(refreshToken);
    }
}

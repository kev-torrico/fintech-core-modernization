package com.modularbank.shared;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.modularbank.shared.infrastructure.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest extends BaseIntegrationTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void generateAccessTokenEmbedsSubjectAndEmailClaim() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateAccessToken(userId, "user@example.com");

        // La validación de firma/expiración ahora vive en el api-gateway (AuthenticationFilter);
        // aquí solo verificamos que el Monolito, como emisor, produce un JWT bien formado.
        DecodedJWT decoded = JWT.decode(token);
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaim("email").asString()).isEqualTo("user@example.com");
        assertThat(decoded.getClaim("roles").asString()).isEqualTo("USER");
    }
}

package com.modularbank.shared.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/**
 * El Monolito sigue siendo el único emisor de tokens del sistema (módulo Auth/Core);
 * la validación de esos tokens en cada petición ya no ocurre aquí, sino en el
 * api-gateway (ver AuthenticationFilter), que es quien decide si la petición pasa y
 * propaga la identidad via headers (X-User-Id / X-User-Email / X-User-Roles).
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration-minutes}")
    private int accessExpirationMinutes;

    public String generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            // Placeholder: el sistema todavía no tiene un modelo de roles/RBAC real.
            .withClaim("roles", "USER")
            .withIssuedAt(now)
            .withExpiresAt(now.plusSeconds(accessExpirationMinutes * 60L))
            .sign(Algorithm.HMAC256(secret));
    }
}

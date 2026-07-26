package com.modularbank.shared.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration-minutes}")
    private int accessExpirationMinutes;

    public String generateAccessToken(UUID userId) {
        Instant now = Instant.now();
        return JWT.create()
            .withSubject(userId.toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plusSeconds(accessExpirationMinutes * 60L))
            .sign(Algorithm.HMAC256(secret));
    }

    public UUID validateAndExtractUserId(String token) {
        DecodedJWT decoded = JWT.require(Algorithm.HMAC256(secret))
            .build()
            .verify(token);
        return UUID.fromString(decoded.getSubject());
    }
}

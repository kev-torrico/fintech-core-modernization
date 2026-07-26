package com.modularbank.shared;

import com.modularbank.shared.infrastructure.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest extends BaseIntegrationTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void generateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateAccessToken(userId);
        UUID extracted = jwtUtil.validateAndExtractUserId(token);
        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    void invalidTokenThrows() {
        assertThatThrownBy(() -> jwtUtil.validateAndExtractUserId("invalid.token.here"))
            .isInstanceOf(Exception.class);
    }
}

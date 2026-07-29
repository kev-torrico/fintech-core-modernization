package com.modularbank.modules.auth.application;

import com.modularbank.modules.auth.application.dto.AuthResponse;
import com.modularbank.modules.auth.application.dto.LoginRequest;
import com.modularbank.modules.auth.application.dto.RegisterRequest;
import com.modularbank.modules.auth.domain.RefreshToken;
import com.modularbank.modules.auth.domain.User;
import com.modularbank.modules.auth.infrastructure.RefreshTokenRepository;
import com.modularbank.modules.auth.infrastructure.UserRepository;
import com.modularbank.shared.infrastructure.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${jwt.refresh-expiration-days}")
    private int refreshExpirationDays;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .name(request.name())
            .build();
        userRepository.save(user);
        return buildAuthResponse(user.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return buildAuthResponse(user.getId());
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        String hashedToken = hashToken(rawToken);
        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashedToken)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        refreshTokenRepository.delete(refreshToken);
        return buildAuthResponse(refreshToken.getUserId());
    }

    private AuthResponse buildAuthResponse(UUID userId) {
        String accessToken = jwtUtil.generateAccessToken(userId);
        String rawRefreshToken = generateSecureToken();
        String hashedToken = hashToken(rawRefreshToken);
        RefreshToken refreshToken = RefreshToken.builder()
            .userId(userId)
            .token(hashedToken)
            .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
            .build();
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(accessToken, rawRefreshToken);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

package com.modularbank.modules.auth.api;

import com.modularbank.modules.auth.application.AuthUseCase;
import com.modularbank.modules.auth.application.dto.AuthResponse;
import com.modularbank.modules.auth.application.dto.LoginRequest;
import com.modularbank.modules.auth.application.dto.RefreshRequest;
import com.modularbank.modules.auth.application.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody @Valid RegisterRequest request) {
        return authUseCase.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        return authUseCase.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody @Valid RefreshRequest request) {
        return authUseCase.refresh(request.refreshToken());
    }
}

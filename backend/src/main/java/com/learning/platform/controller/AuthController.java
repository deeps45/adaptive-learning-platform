package com.learning.platform.controller;

import com.learning.platform.dto.AuthDtos.LoginRequest;
import com.learning.platform.dto.AuthDtos.RefreshRequest;
import com.learning.platform.dto.AuthDtos.RegisterRequest;
import com.learning.platform.dto.AuthDtos.TokenResponse;
import com.learning.platform.dto.AuthDtos.UserResponse;
import com.learning.platform.entity.User;
import com.learning.platform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        UserResponse response = new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}

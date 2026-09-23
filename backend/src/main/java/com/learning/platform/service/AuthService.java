package com.learning.platform.service;

import com.learning.platform.dto.AuthDtos.LoginRequest;
import com.learning.platform.dto.AuthDtos.RegisterRequest;
import com.learning.platform.dto.AuthDtos.TokenResponse;
import com.learning.platform.entity.User;
import com.learning.platform.exception.ConflictException;
import com.learning.platform.repository.UserRepository;
import com.learning.platform.security.JwtService;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        User user =
                User.builder()
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .fullName(request.fullName())
                        .role(request.role())
                        .build();
        return userRepository.save(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                                "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Invalid email or password");
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        Claims claims = jwtService.parseClaims(refreshToken);
        if (claims == null || !jwtService.isRefreshToken(claims)) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Invalid or expired refresh token");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                                "User no longer exists"));
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String role = user.getRole().name();
        String access = jwtService.generateAccessToken(user.getId(), user.getEmail(), role);
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getEmail(), role);
        return new TokenResponse(access, refresh);
    }
}

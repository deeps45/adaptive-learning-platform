package com.learning.platform.dto;

import com.learning.platform.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
            @NotBlank String fullName,
            @NotNull Role role) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken) {}

    public record UserResponse(UUID id, String email, String fullName, Role role) {}
}

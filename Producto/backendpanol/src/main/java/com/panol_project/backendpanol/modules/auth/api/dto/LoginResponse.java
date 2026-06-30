package com.panol_project.backendpanol.modules.auth.api.dto;

public record LoginResponse(
        String role,
        long expiresInSeconds,
        LoginUserResponse user
) {
}

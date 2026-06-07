package com.panol_project.backendpanol.modules.auth.api.dto;

public record LoginResponse(
        String accessToken,
        String role,
        long expiresInSeconds,
        LoginUserResponse user
) {
}

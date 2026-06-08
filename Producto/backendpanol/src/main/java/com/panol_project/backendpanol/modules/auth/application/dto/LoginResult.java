package com.panol_project.backendpanol.modules.auth.application.dto;

public record LoginResult(
        String accessToken,
        String role,
        long expiresInSeconds,
        AuthenticatedUserSummary user
) {
}

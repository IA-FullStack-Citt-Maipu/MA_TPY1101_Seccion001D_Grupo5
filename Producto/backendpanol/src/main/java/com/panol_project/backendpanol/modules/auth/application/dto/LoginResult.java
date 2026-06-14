package com.panol_project.backendpanol.modules.auth.application.dto;

public record LoginResult(
        String accessToken,
        String refreshToken,
        String role,
        long expiresInSeconds,
        AuthenticatedUserSummary user,
        boolean persistentLogin
) {
}

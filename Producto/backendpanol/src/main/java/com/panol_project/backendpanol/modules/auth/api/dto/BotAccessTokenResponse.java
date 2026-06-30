package com.panol_project.backendpanol.modules.auth.api.dto;

public record BotAccessTokenResponse(
        String token,
        long expiresInSeconds
) {
}

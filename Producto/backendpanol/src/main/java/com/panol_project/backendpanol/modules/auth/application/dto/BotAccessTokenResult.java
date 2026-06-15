package com.panol_project.backendpanol.modules.auth.application.dto;

public record BotAccessTokenResult(
        String token,
        long expiresInSeconds
) {
}

package com.panol_project.backendpanol.modules.auth.application.dto;

public record RefreshResult(
        String accessToken,
        String refreshToken,
        boolean persistentLogin
) {
}

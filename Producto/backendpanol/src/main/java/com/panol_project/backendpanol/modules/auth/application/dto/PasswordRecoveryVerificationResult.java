package com.panol_project.backendpanol.modules.auth.application.dto;

public record PasswordRecoveryVerificationResult(
        String resetToken,
        int expiresInSeconds
) {
}

package com.panol_project.backendpanol.modules.auth.application.dto;

public record PasswordRecoveryResetCommand(
        String resetToken,
        String newPassword
) {
}

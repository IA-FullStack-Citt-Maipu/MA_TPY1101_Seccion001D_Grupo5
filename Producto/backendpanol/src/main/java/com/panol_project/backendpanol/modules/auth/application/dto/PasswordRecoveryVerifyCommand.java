package com.panol_project.backendpanol.modules.auth.application.dto;

public record PasswordRecoveryVerifyCommand(
        String rut,
        String code
) {
}

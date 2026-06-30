package com.panol_project.backendpanol.modules.auth.application.dto;

public record LoginCommand(
        String rut,
        String password,
        boolean rememberMe,
        String deviceInfo
) {
}

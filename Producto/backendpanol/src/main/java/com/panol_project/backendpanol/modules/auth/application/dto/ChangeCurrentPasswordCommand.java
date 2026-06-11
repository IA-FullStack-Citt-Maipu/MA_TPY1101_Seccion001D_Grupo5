package com.panol_project.backendpanol.modules.auth.application.dto;

public record ChangeCurrentPasswordCommand(
        String currentPassword,
        String newPassword
) {
}

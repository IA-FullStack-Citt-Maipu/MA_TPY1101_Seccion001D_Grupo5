package com.panol_project.backendpanol.modules.auth.application.dto;

public record RevokeCurrentUserSessionResult(
        boolean currentSessionRevoked
) {
}

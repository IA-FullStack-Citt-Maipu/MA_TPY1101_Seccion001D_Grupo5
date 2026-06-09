package com.panol_project.backendpanol.modules.auth.application.dto;

import java.util.UUID;

public record AuthenticatedUserSummary(
        UUID id,
        String name,
        String email,
        String role
) {
}

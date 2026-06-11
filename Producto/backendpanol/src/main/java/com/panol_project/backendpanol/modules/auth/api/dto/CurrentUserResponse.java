package com.panol_project.backendpanol.modules.auth.api.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String name,
        String email,
        String role
) {
}

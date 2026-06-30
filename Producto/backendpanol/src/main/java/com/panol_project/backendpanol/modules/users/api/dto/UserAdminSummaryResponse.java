package com.panol_project.backendpanol.modules.users.api.dto;

import java.time.OffsetDateTime;

public record UserAdminSummaryResponse(
        String uuid,
        String name,
        String rut,
        String email,
        String role,
        boolean active,
        OffsetDateTime createdAt
) {
}

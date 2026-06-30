package com.panol_project.backendpanol.modules.users.domain;

import java.time.OffsetDateTime;

public record UserAdminSummary(
        String uuid,
        String name,
        String rut,
        String email,
        String role,
        boolean active,
        OffsetDateTime createdAt
) {
}

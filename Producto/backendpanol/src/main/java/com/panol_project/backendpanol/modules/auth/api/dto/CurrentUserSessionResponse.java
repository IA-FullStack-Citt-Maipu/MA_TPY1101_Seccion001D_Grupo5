package com.panol_project.backendpanol.modules.auth.api.dto;

import java.time.OffsetDateTime;

public record CurrentUserSessionResponse(
        String id,
        boolean current,
        boolean persistentLogin,
        String userAgent,
        OffsetDateTime createdAt,
        OffsetDateTime accessExpiresAt,
        OffsetDateTime sessionExpiresAt
) {
}

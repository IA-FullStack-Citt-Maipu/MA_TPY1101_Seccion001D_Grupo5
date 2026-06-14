package com.panol_project.backendpanol.modules.auth.application.dto;

import java.time.OffsetDateTime;

public record CurrentUserSessionSummary(
        String id,
        boolean current,
        boolean persistentLogin,
        String userAgent,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
}

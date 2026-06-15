package com.panol_project.backendpanol.modules.auth.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefreshSession(
        long id,
        UUID userUuid,
        String refreshTokenHash,
        String userAgent,
        String currentAccessJti,
        OffsetDateTime currentAccessExpiresAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        boolean persistentLogin
) {
}

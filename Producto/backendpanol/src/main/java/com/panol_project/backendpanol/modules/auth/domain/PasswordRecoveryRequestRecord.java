package com.panol_project.backendpanol.modules.auth.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PasswordRecoveryRequestRecord(
        long id,
        UUID userUuid,
        String userName,
        String userEmail,
        String userRut,
        String userPasswordHash,
        String codeHash,
        String resetTokenHash,
        OffsetDateTime expiresAt,
        OffsetDateTime verifiedAt,
        OffsetDateTime consumedAt,
        OffsetDateTime lastSentAt,
        int attemptCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

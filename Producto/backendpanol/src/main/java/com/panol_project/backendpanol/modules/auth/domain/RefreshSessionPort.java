package com.panol_project.backendpanol.modules.auth.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionPort {
    void createSession(
            UUID userUuid,
            String refreshTokenHash,
            OffsetDateTime expiresAt,
            String userAgent,
            boolean persistentLogin,
            String currentAccessJti,
            OffsetDateTime currentAccessExpiresAt
    );

    Optional<RefreshSession> findSessionByTokenHash(String refreshTokenHash);

    void rotateSession(
            long sessionId,
            String refreshTokenHash,
            OffsetDateTime expiresAt,
            String userAgent,
            boolean persistentLogin,
            String currentAccessJti,
            OffsetDateTime currentAccessExpiresAt
    );

    List<RefreshSession> findSessionsByUserUuid(UUID userUuid);

    Optional<RefreshSession> findSessionByIdAndUserUuid(long sessionId, UUID userUuid);

    Optional<RefreshSession> deleteSessionByTokenHash(String refreshTokenHash);

    void deleteSessionById(long sessionId);
}

package com.panol_project.backendpanol.modules.auth.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserAuthPort {
    Optional<AuthUser> findAuthUserByRut(String rut);
    Optional<AuthUser> findAuthUserByUuid(UUID userUuid);
    Optional<PasswordRecoveryRequestRecord> findLatestPasswordRecoveryRequestByUserUuid(UUID userUuid);
    Optional<PasswordRecoveryRequestRecord> findPasswordRecoveryRequestByResetTokenHash(String resetTokenHash);
    void invalidatePasswordRecoveryRequests(UUID userUuid, OffsetDateTime invalidatedAt);
    void createPasswordRecoveryRequest(UUID userUuid, String codeHash, OffsetDateTime expiresAt, OffsetDateTime lastSentAt);
    void incrementPasswordRecoveryAttempt(long requestId, int nextAttemptCount, OffsetDateTime updatedAt);
    void verifyPasswordRecoveryRequest(long requestId, String resetTokenHash, OffsetDateTime verifiedAt, OffsetDateTime updatedAt);
    void consumePasswordRecoveryRequest(long requestId, OffsetDateTime consumedAt);
    boolean existsOtherUserWithEmail(String normalizedEmail, UUID excludeUserUuid);
    void registerFailedAttempt(UUID userUuid, int attempts, OffsetDateTime blockedUntil);
    void resetLoginAttempts(UUID userUuid, OffsetDateTime lastLoginAt);
    void updateEmail(UUID userUuid, String normalizedEmail);
    void updatePasswordHash(UUID userUuid, String passwordHash);
}

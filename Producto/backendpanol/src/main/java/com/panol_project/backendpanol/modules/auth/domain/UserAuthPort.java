package com.panol_project.backendpanol.modules.auth.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserAuthPort {
    Optional<AuthUser> findAuthUserByRut(String rut);
    Optional<AuthUser> findAuthUserByUuid(UUID userUuid);
    boolean existsOtherUserWithEmail(String normalizedEmail, UUID excludeUserUuid);
    void registerFailedAttempt(UUID userUuid, int attempts, OffsetDateTime blockedUntil);
    void resetLoginAttempts(UUID userUuid, OffsetDateTime lastLoginAt);
    void updateEmail(UUID userUuid, String normalizedEmail);
    void updatePasswordHash(UUID userUuid, String passwordHash);
}

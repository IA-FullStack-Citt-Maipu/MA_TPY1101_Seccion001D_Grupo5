package com.panol_project.backendpanol.modules.auth.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TokenRevocationPort {
    void revokeToken(String jti, UUID userUuid, OffsetDateTime expiresAt);
    boolean isRevoked(String jti);
    int deleteExpiredRevocations(OffsetDateTime now, int limit);
}

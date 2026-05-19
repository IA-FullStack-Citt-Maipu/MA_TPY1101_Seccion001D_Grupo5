package com.panol_project.backendpanol.modules.auth.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.TokenRevocation.TOKEN_REVOCATION;
import static com.panol_project.backendpanol.jooq.tables.User.USER;

import com.panol_project.backendpanol.modules.auth.domain.AuthUser;
import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import com.panol_project.backendpanol.modules.auth.domain.UserAuthPort;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class AuthJooqRepository implements UserAuthPort, TokenRevocationPort {

    private final DSLContext dsl;

    public AuthJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<AuthUser> findAuthUserByRut(String rut) {
        return dsl.resultQuery("""
                        select user_uuid, rut, password_hash, role_name, failed_login_attempts, blocked_until
                        from public.fn_auth_find_user_by_rut(?)
                        """, rut)
                .fetchOptional(record -> new AuthUser(
                        record.get("user_uuid", UUID.class),
                        record.get("rut", String.class),
                        record.get("password_hash", String.class),
                        record.get("role_name", String.class),
                        record.get("failed_login_attempts", Integer.class) == null ? 0 : record.get("failed_login_attempts", Integer.class),
                        record.get("blocked_until", OffsetDateTime.class)
                ));
    }

    @Override
    public void registerFailedAttempt(UUID userUuid, int attempts, OffsetDateTime blockedUntil) {
        dsl.update(USER)
                .set(USER.FAILED_LOGIN_ATTEMPTS, attempts)
                .set(USER.BLOCKED_UNTIL, blockedUntil)
                .where(USER.UUID.eq(userUuid))
                .execute();
    }

    @Override
    public void resetLoginAttempts(UUID userUuid, OffsetDateTime lastLoginAt) {
        dsl.update(USER)
                .set(USER.FAILED_LOGIN_ATTEMPTS, 0)
                .set(USER.BLOCKED_UNTIL, (OffsetDateTime) null)
                .set(USER.LAST_LOGIN_AT, lastLoginAt)
                .where(USER.UUID.eq(userUuid))
                .execute();
    }

    @Override
    public void revokeToken(String jti, UUID userUuid, OffsetDateTime expiresAt) {
        Long userId = findUserIdByUuid(userUuid);
        dsl.insertInto(TOKEN_REVOCATION)
                .set(TOKEN_REVOCATION.JTI, jti)
                .set(TOKEN_REVOCATION.USER_ID, userId)
                .set(TOKEN_REVOCATION.EXPIRES_AT, expiresAt)
                .onConflict(TOKEN_REVOCATION.JTI)
                .doNothing()
                .execute();
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Integer count = dsl.selectCount()
                .from(TOKEN_REVOCATION)
                .where(TOKEN_REVOCATION.JTI.eq(jti))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    private Long findUserIdByUuid(UUID userUuid) {
        if (userUuid == null) {
            return null;
        }
        return dsl.select(USER.ID)
                .from(USER)
                .where(USER.UUID.eq(userUuid))
                .fetchOne(USER.ID);
    }

}

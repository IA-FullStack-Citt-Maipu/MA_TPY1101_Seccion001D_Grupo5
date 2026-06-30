package com.panol_project.backendpanol.modules.auth.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.TokenRevocation.TOKEN_REVOCATION;
import static com.panol_project.backendpanol.jooq.tables.Role.ROLE;
import static com.panol_project.backendpanol.jooq.tables.User.USER;

import com.panol_project.backendpanol.modules.auth.domain.AuthUser;
import com.panol_project.backendpanol.modules.auth.domain.PasswordRecoveryRequestRecord;
import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import com.panol_project.backendpanol.modules.auth.domain.UserAuthPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
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
                        select *
                        from public.fn_auth_find_user_by_rut(cast(? as text))
                        """, rut)
                .fetchOptional(record -> new AuthUser(
                        record.get("user_uuid", UUID.class),
                        record.get("rut", String.class),
                        record.get("user_name", String.class),
                        record.get("email", String.class),
                        record.get("password_hash", String.class),
                        record.get("role_name", String.class),
                        record.get("failed_login_attempts", Integer.class) == null ? 0 : record.get("failed_login_attempts", Integer.class),
                        record.get("blocked_until", OffsetDateTime.class)
                ));
    }

    @Override
    public Optional<AuthUser> findAuthUserByUuid(UUID userUuid) {
        if (userUuid == null) {
            return Optional.empty();
        }
        return dsl.select(
                        USER.UUID,
                        USER.RUT,
                        USER.NAME,
                        USER.EMAIL,
                        USER.PASSWORD_HASH,
                        ROLE.NAME,
                        USER.FAILED_LOGIN_ATTEMPTS,
                        USER.BLOCKED_UNTIL
                )
                .from(USER)
                .join(ROLE).on(ROLE.ID.eq(USER.ROLE_ID))
                .where(USER.UUID.eq(userUuid).and(USER.ACTIVE.isTrue()))
                .fetchOptional(record -> new AuthUser(
                        record.get(USER.UUID),
                        record.get(USER.RUT),
                        record.get(USER.NAME),
                        record.get(USER.EMAIL),
                        record.get(USER.PASSWORD_HASH),
                        record.get(ROLE.NAME),
                        record.get(USER.FAILED_LOGIN_ATTEMPTS) == null ? 0 : record.get(USER.FAILED_LOGIN_ATTEMPTS),
                        record.get(USER.BLOCKED_UNTIL)
                ));
    }

    @Override
    public Optional<PasswordRecoveryRequestRecord> findLatestPasswordRecoveryRequestByUserUuid(UUID userUuid) {
        if (userUuid == null) {
            return Optional.empty();
        }

        return dsl.resultQuery("""
                        select
                            prr.id,
                            prr.code_hash,
                            prr.reset_token_hash,
                            prr.expires_at,
                            prr.verified_at,
                            prr.consumed_at,
                            prr.last_sent_at,
                            prr.attempt_count,
                            prr.created_at,
                            prr.updated_at,
                            u.uuid as user_uuid,
                            u.name as user_name,
                            u.email as user_email,
                            u.rut as user_rut,
                            u.password_hash as user_password_hash
                        from public.password_reset_request prr
                        join public."user" u on u.id = prr.user_id
                        where u.uuid = cast(? as uuid)
                        order by prr.created_at desc, prr.id desc
                        limit 1
                        """, userUuid)
                .fetchOptional(this::toPasswordRecoveryRequestRecord);
    }

    @Override
    public Optional<PasswordRecoveryRequestRecord> findPasswordRecoveryRequestByResetTokenHash(String resetTokenHash) {
        if (resetTokenHash == null || resetTokenHash.isBlank()) {
            return Optional.empty();
        }

        return dsl.resultQuery("""
                        select
                            prr.id,
                            prr.code_hash,
                            prr.reset_token_hash,
                            prr.expires_at,
                            prr.verified_at,
                            prr.consumed_at,
                            prr.last_sent_at,
                            prr.attempt_count,
                            prr.created_at,
                            prr.updated_at,
                            u.uuid as user_uuid,
                            u.name as user_name,
                            u.email as user_email,
                            u.rut as user_rut,
                            u.password_hash as user_password_hash
                        from public.password_reset_request prr
                        join public."user" u on u.id = prr.user_id
                        where prr.reset_token_hash = cast(? as text)
                        order by prr.created_at desc, prr.id desc
                        limit 1
                        """, resetTokenHash)
                .fetchOptional(this::toPasswordRecoveryRequestRecord);
    }

    @Override
    public void invalidatePasswordRecoveryRequests(UUID userUuid, OffsetDateTime invalidatedAt) {
        Long userId = findUserIdByUuid(userUuid);
        if (userId == null || invalidatedAt == null) {
            return;
        }

        dsl.execute("""
                update public.password_reset_request
                   set consumed_at = cast(? as timestamptz),
                       updated_at = cast(? as timestamptz)
                 where user_id = cast(? as bigint)
                   and consumed_at is null
                """, invalidatedAt, invalidatedAt, userId);
    }

    @Override
    public void createPasswordRecoveryRequest(UUID userUuid, String codeHash, OffsetDateTime expiresAt, OffsetDateTime lastSentAt) {
        Long userId = findUserIdByUuid(userUuid);
        if (userId == null) {
            throw new IllegalStateException("No existe user_id para password reset request");
        }

        dsl.execute("""
                insert into public.password_reset_request (
                    user_id,
                    code_hash,
                    expires_at,
                    last_sent_at
                )
                values (
                    cast(? as bigint),
                    cast(? as text),
                    cast(? as timestamptz),
                    cast(? as timestamptz)
                )
                """, userId, codeHash, expiresAt, lastSentAt);
    }

    @Override
    public void incrementPasswordRecoveryAttempt(long requestId, int nextAttemptCount, OffsetDateTime updatedAt) {
        dsl.execute("""
                update public.password_reset_request
                   set attempt_count = cast(? as integer),
                       updated_at = cast(? as timestamptz)
                 where id = cast(? as bigint)
                """, nextAttemptCount, updatedAt, requestId);
    }

    @Override
    public void verifyPasswordRecoveryRequest(long requestId, String resetTokenHash, OffsetDateTime verifiedAt, OffsetDateTime updatedAt) {
        dsl.execute("""
                update public.password_reset_request
                   set reset_token_hash = cast(? as text),
                       verified_at = cast(? as timestamptz),
                       updated_at = cast(? as timestamptz)
                 where id = cast(? as bigint)
                """, resetTokenHash, verifiedAt, updatedAt, requestId);
    }

    @Override
    public void consumePasswordRecoveryRequest(long requestId, OffsetDateTime consumedAt) {
        dsl.execute("""
                update public.password_reset_request
                   set consumed_at = cast(? as timestamptz),
                       updated_at = cast(? as timestamptz)
                 where id = cast(? as bigint)
                """, consumedAt, consumedAt, requestId);
    }

    @Override
    public boolean existsOtherUserWithEmail(String normalizedEmail, UUID excludeUserUuid) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(USER)
                        .where(USER.EMAIL.equalIgnoreCase(normalizedEmail))
                        .and(excludeUserUuid == null ? USER.UUID.isNotNull() : USER.UUID.ne(excludeUserUuid))
        );
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
    public void updateEmail(UUID userUuid, String normalizedEmail) {
        dsl.update(USER)
                .set(USER.EMAIL, normalizedEmail)
                .where(USER.UUID.eq(userUuid))
                .execute();
    }

    @Override
    public void updatePasswordHash(UUID userUuid, String passwordHash) {
        dsl.update(USER)
                .set(USER.PASSWORD_HASH, passwordHash)
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
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(TOKEN_REVOCATION)
                        .where(TOKEN_REVOCATION.JTI.eq(jti))
        );
    }

    @Override
    public int deleteExpiredRevocations(OffsetDateTime now, int limit) {
        if (now == null) {
            throw new IllegalArgumentException("now es obligatorio");
        }

        int normalizedLimit = limit > 0 ? limit : 500;
        List<Long> revocationIds = dsl.select(TOKEN_REVOCATION.ID)
                .from(TOKEN_REVOCATION)
                .where(TOKEN_REVOCATION.EXPIRES_AT.lt(now))
                .orderBy(TOKEN_REVOCATION.EXPIRES_AT.asc(), TOKEN_REVOCATION.ID.asc())
                .limit(normalizedLimit)
                .fetch(TOKEN_REVOCATION.ID);

        if (revocationIds.isEmpty()) {
            return 0;
        }

        return dsl.deleteFrom(TOKEN_REVOCATION)
                .where(TOKEN_REVOCATION.ID.in(revocationIds))
                .execute();
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

    private PasswordRecoveryRequestRecord toPasswordRecoveryRequestRecord(Record record) {
        return new PasswordRecoveryRequestRecord(
                record.get("id", Long.class),
                record.get("user_uuid", UUID.class),
                record.get("user_name", String.class),
                record.get("user_email", String.class),
                record.get("user_rut", String.class),
                record.get("user_password_hash", String.class),
                record.get("code_hash", String.class),
                record.get("reset_token_hash", String.class),
                record.get("expires_at", OffsetDateTime.class),
                record.get("verified_at", OffsetDateTime.class),
                record.get("consumed_at", OffsetDateTime.class),
                record.get("last_sent_at", OffsetDateTime.class),
                record.get("attempt_count", Integer.class) == null ? 0 : record.get("attempt_count", Integer.class),
                record.get("created_at", OffsetDateTime.class),
                record.get("updated_at", OffsetDateTime.class)
        );
    }

}

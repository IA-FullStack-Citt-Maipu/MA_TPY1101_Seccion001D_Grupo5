package com.panol_project.backendpanol.modules.auth.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.User.USER;
import static com.panol_project.backendpanol.jooq.tables.UserSession.USER_SESSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.modules.auth.domain.RefreshSession;
import com.panol_project.backendpanol.modules.auth.domain.RefreshSessionPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshSessionJooqRepository implements RefreshSessionPort {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public RefreshSessionJooqRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void createSession(
            UUID userUuid,
            String refreshTokenHash,
            OffsetDateTime expiresAt,
            String userAgent,
            boolean persistentLogin,
            String currentAccessJti,
            OffsetDateTime currentAccessExpiresAt
    ) {
        Long userId = findUserIdByUuid(userUuid);
        if (userId == null) {
            throw new IllegalStateException("No existe user_id para refresh session");
        }

        dsl.insertInto(USER_SESSION)
                .set(USER_SESSION.USER_ID, userId)
                .set(USER_SESSION.REFRESH_TOKEN_HASH, refreshTokenHash)
                .set(USER_SESSION.EXPIRES_AT, expiresAt)
                .set(USER_SESSION.DEVICE_INFO, serializeDeviceInfo(userAgent, persistentLogin, currentAccessJti, currentAccessExpiresAt))
                .execute();
    }

    @Override
    public Optional<RefreshSession> findSessionByTokenHash(String refreshTokenHash) {
        if (refreshTokenHash == null || refreshTokenHash.isBlank()) {
            return Optional.empty();
        }

        return dsl.select(
                        USER_SESSION.ID,
                        USER.UUID,
                        USER_SESSION.REFRESH_TOKEN_HASH,
                        USER_SESSION.EXPIRES_AT,
                        USER_SESSION.CREATED_AT,
                        USER_SESSION.DEVICE_INFO
                )
                .from(USER_SESSION)
                .join(USER).on(USER.ID.eq(USER_SESSION.USER_ID))
                .where(USER_SESSION.REFRESH_TOKEN_HASH.eq(refreshTokenHash))
                .fetchOptional(this::toRefreshSession);
    }

    @Override
    public void rotateSession(
            long sessionId,
            String refreshTokenHash,
            OffsetDateTime expiresAt,
            String userAgent,
            boolean persistentLogin,
            String currentAccessJti,
            OffsetDateTime currentAccessExpiresAt
    ) {
        dsl.update(USER_SESSION)
                .set(USER_SESSION.REFRESH_TOKEN_HASH, refreshTokenHash)
                .set(USER_SESSION.EXPIRES_AT, expiresAt)
                .set(USER_SESSION.DEVICE_INFO, serializeDeviceInfo(userAgent, persistentLogin, currentAccessJti, currentAccessExpiresAt))
                .where(USER_SESSION.ID.eq(sessionId))
                .execute();
    }

    @Override
    public List<RefreshSession> findSessionsByUserUuid(UUID userUuid) {
        if (userUuid == null) {
            return List.of();
        }

        return dsl.select(
                        USER_SESSION.ID,
                        USER.UUID,
                        USER_SESSION.REFRESH_TOKEN_HASH,
                        USER_SESSION.EXPIRES_AT,
                        USER_SESSION.CREATED_AT,
                        USER_SESSION.DEVICE_INFO
                )
                .from(USER_SESSION)
                .join(USER).on(USER.ID.eq(USER_SESSION.USER_ID))
                .where(USER.UUID.eq(userUuid))
                .orderBy(USER_SESSION.CREATED_AT.desc(), USER_SESSION.ID.desc())
                .fetch(this::toRefreshSession);
    }

    @Override
    public Optional<RefreshSession> findSessionByIdAndUserUuid(long sessionId, UUID userUuid) {
        if (sessionId <= 0 || userUuid == null) {
            return Optional.empty();
        }

        return dsl.select(
                        USER_SESSION.ID,
                        USER.UUID,
                        USER_SESSION.REFRESH_TOKEN_HASH,
                        USER_SESSION.EXPIRES_AT,
                        USER_SESSION.CREATED_AT,
                        USER_SESSION.DEVICE_INFO
                )
                .from(USER_SESSION)
                .join(USER).on(USER.ID.eq(USER_SESSION.USER_ID))
                .where(USER_SESSION.ID.eq(sessionId).and(USER.UUID.eq(userUuid)))
                .fetchOptional(this::toRefreshSession);
    }

    @Override
    public Optional<RefreshSession> deleteSessionByTokenHash(String refreshTokenHash) {
        Optional<RefreshSession> session = findSessionByTokenHash(refreshTokenHash);
        session.ifPresent(value ->
                dsl.deleteFrom(USER_SESSION)
                        .where(USER_SESSION.ID.eq(value.id()))
                        .execute());
        return session;
    }

    @Override
    public void deleteSessionById(long sessionId) {
        dsl.deleteFrom(USER_SESSION)
                .where(USER_SESSION.ID.eq(sessionId))
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

    private RefreshSession toRefreshSession(Record record) {
        ParsedDeviceInfo deviceInfo = parseDeviceInfo(record.get(USER_SESSION.DEVICE_INFO));
        return new RefreshSession(
                record.get(USER_SESSION.ID),
                record.get(USER.UUID),
                record.get(USER_SESSION.REFRESH_TOKEN_HASH),
                deviceInfo.userAgent(),
                deviceInfo.currentAccessJti(),
                deviceInfo.currentAccessExpiresAt(),
                record.get(USER_SESSION.EXPIRES_AT),
                record.get(USER_SESSION.CREATED_AT),
                deviceInfo.persistentLogin()
        );
    }

    private String serializeDeviceInfo(
            String userAgent,
            boolean persistentLogin,
            String currentAccessJti,
            OffsetDateTime currentAccessExpiresAt
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "persistentLogin", persistentLogin,
                    "userAgent", userAgent == null ? "" : userAgent.trim(),
                    "currentAccessJti", currentAccessJti == null ? "" : currentAccessJti.trim(),
                    "currentAccessExpiresAt", currentAccessExpiresAt == null ? "" : currentAccessExpiresAt.toString()
            ));
        } catch (Exception ex) {
            return userAgent == null ? "" : userAgent.trim();
        }
    }

    private ParsedDeviceInfo parseDeviceInfo(String rawDeviceInfo) {
        if (rawDeviceInfo == null || rawDeviceInfo.isBlank()) {
            return new ParsedDeviceInfo(false, null, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(rawDeviceInfo);
            return new ParsedDeviceInfo(
                    node.path("persistentLogin").asBoolean(false),
                    readNullableText(node.path("userAgent")),
                    readNullableText(node.path("currentAccessJti")),
                    parseOffsetDateTime(readNullableText(node.path("currentAccessExpiresAt")))
            );
        } catch (Exception ex) {
            return new ParsedDeviceInfo(false, rawDeviceInfo.trim(), null, null);
        }
    }

    private String readNullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isBlank() ? null : value;
    }

    private OffsetDateTime parseOffsetDateTime(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue);
        } catch (Exception ex) {
            return null;
        }
    }

    private record ParsedDeviceInfo(
            boolean persistentLogin,
            String userAgent,
            String currentAccessJti,
            OffsetDateTime currentAccessExpiresAt
    ) {
    }
}

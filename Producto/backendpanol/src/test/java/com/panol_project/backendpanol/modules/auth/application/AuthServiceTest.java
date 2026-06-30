package com.panol_project.backendpanol.modules.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.auth.application.dto.AuthenticatedUserSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.BotAccessTokenResult;
import com.panol_project.backendpanol.modules.auth.application.dto.ChangeCurrentPasswordCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.CurrentUserSessionSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginResult;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryRequestCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryResetCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryVerificationResult;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryVerifyCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.RefreshResult;
import com.panol_project.backendpanol.modules.auth.application.dto.RevokeCurrentUserSessionResult;
import com.panol_project.backendpanol.modules.auth.application.dto.UpdateCurrentEmailCommand;
import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import com.panol_project.backendpanol.modules.auth.domain.AuthUser;
import com.panol_project.backendpanol.modules.auth.domain.PasswordRecoveryNotificationPort;
import com.panol_project.backendpanol.modules.auth.domain.PasswordRecoveryRequestRecord;
import com.panol_project.backendpanol.modules.auth.domain.RefreshSession;
import com.panol_project.backendpanol.modules.auth.domain.RefreshSessionPort;
import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import com.panol_project.backendpanol.modules.auth.domain.UserAuthPort;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAuthPort userAuthPort;

    @Mock
    private RefreshSessionPort refreshSessionPort;

    @Mock
    private TokenRevocationPort tokenRevocationPort;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private PasswordRecoveryNotificationPort passwordRecoveryNotificationPort;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private OutboxService outboxService;

    @Test
    void loginDebeRetornarResultadoDeAplicacionYCrearSesionRefresh() {
        UUID userUuid = UUID.randomUUID();
        String hash = BCrypt.hashpw("secret", BCrypt.gensalt());
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Directora Prueba",
                "directora@panol.test",
                hash,
                "DIRECTOR",
                0,
                null
        );

        when(userAuthPort.findAuthUserByRut("123456789")).thenReturn(Optional.of(authUser));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("token-123")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthService service = buildService();

        LoginResult result = service.login(new LoginCommand("12.345.678-9", "secret", true, "JUnit"));

        assertEquals("token-123", result.accessToken());
        assertFalse(result.refreshToken().isBlank());
        assertEquals("DIRECTOR", result.role());
        assertEquals(3600, result.expiresInSeconds());
        assertEquals("Directora Prueba", result.user().name());
        assertEquals("directora@panol.test", result.user().email());
        assertEquals("DIRECTOR", result.user().role());
        assertEquals(true, result.persistentLogin());
        verify(userAuthPort).resetLoginAttempts(eq(userUuid), any(OffsetDateTime.class));
        verify(refreshSessionPort).createSession(eq(userUuid), anyString(), any(OffsetDateTime.class), eq("JUnit"), eq(true), anyString(), any(OffsetDateTime.class));
        verify(auditLogPort).log("user_logged_in", userUuid, userUuid, Map.of("rut", "123456789", "role", "DIRECTOR"));
        verify(outboxService).enqueue("user", userUuid, "UserLoggedIn", userUuid, Map.of("rut", "123456789", "role", "DIRECTOR"));
    }

    @Test
    void loginConRutSinDvNoDebeRecortarElUltimoDigito() {
        UUID userUuid = UUID.randomUUID();
        String hash = BCrypt.hashpw("secret", BCrypt.gensalt());
        AuthUser authUser = new AuthUser(
                userUuid,
                "11111111",
                "Coordinador QA",
                "qa@panol.test",
                hash,
                "COORDINADOR",
                0,
                null
        );

        when(userAuthPort.findAuthUserByRut("11111111")).thenReturn(Optional.of(authUser));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("token-qa")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthService service = buildService();

        LoginResult result = service.login(new LoginCommand("11111111", "secret", true, "JUnit"));

        assertEquals("token-qa", result.accessToken());
        verify(userAuthPort).findAuthUserByRut("11111111");
        verify(userAuthPort).resetLoginAttempts(eq(userUuid), any(OffsetDateTime.class));
    }

    @Test
    void loginConSesionTemporalDebeUsarTtlTemporalParaRefreshSession() {
        UUID userUuid = UUID.randomUUID();
        String hash = BCrypt.hashpw("secret", BCrypt.gensalt());
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Temporal",
                "docente.temporal@panol.test",
                hash,
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByRut("123456789")).thenReturn(Optional.of(authUser));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("token-123")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthService service = buildService();
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        LoginResult result = service.login(new LoginCommand("12.345.678-9", "secret", false, "JUnit"));

        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);
        ArgumentCaptor<OffsetDateTime> expiresAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

        assertFalse(result.refreshToken().isBlank());
        assertFalse(result.persistentLogin());
        verify(refreshSessionPort).createSession(
                eq(userUuid),
                anyString(),
                expiresAtCaptor.capture(),
                eq("JUnit"),
                eq(false),
                anyString(),
                any(OffsetDateTime.class)
        );

        OffsetDateTime expiresAt = expiresAtCaptor.getValue();
        assertFalse(expiresAt.isBefore(before.plusSeconds(86400)));
        assertFalse(expiresAt.isAfter(after.plusSeconds(86401)));
    }

    @Test
    void loginConPasswordIncorrectaDebeLanzarErrorYRegistrarEvento() {
        UUID userUuid = UUID.randomUUID();
        String hash = BCrypt.hashpw("secret", BCrypt.gensalt());
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Prueba",
                "docente@panol.test",
                hash,
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByRut("123456789")).thenReturn(Optional.of(authUser));

        AuthService service = buildService();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.login(new LoginCommand("12.345.678-9", "wrong-pass", true, "JUnit"))
        );

        assertEquals("AUTH_INVALID_CREDENTIALS", ex.getCode());
        verify(userAuthPort).registerFailedAttempt(eq(userUuid), eq(1), eq(null));
        verify(refreshSessionPort, never()).createSession(any(), anyString(), any(), any(), anyBoolean(), anyString(), any(OffsetDateTime.class));
        verify(auditLogPort).log("login_failed", null, null, Map.of("rut", "123456789"));
        verify(outboxService).enqueue("auth", null, "LoginFailed", null, Map.of("rut", "123456789"));
    }

    @Test
    void refreshDebeRotarSesionYEmitirNuevoParDeTokens() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Prueba",
                "docente@panol.test",
                BCrypt.hashpw("secret", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(refreshSessionPort.findSessionByTokenHash(anyString())).thenReturn(Optional.of(new RefreshSession(
                44L,
                userUuid,
                sha256("refresh-raw-token"),
                "Browser/1.0",
                "old-access-jti",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10),
                true
        )));
        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("fresh-access-token")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthService service = buildService();

        RefreshResult result = service.refresh("refresh-raw-token", "Browser/1.0");

        assertEquals("fresh-access-token", result.accessToken());
        assertFalse(result.refreshToken().isBlank());
        assertEquals(true, result.persistentLogin());
        verify(refreshSessionPort).rotateSession(eq(44L), anyString(), any(OffsetDateTime.class), eq("Browser/1.0"), eq(true), anyString(), any(OffsetDateTime.class));
    }

    @Test
    void refreshDeSesionTemporalDebeMantenerTtlTemporal() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Temporal",
                "docente.temporal@panol.test",
                BCrypt.hashpw("secret", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(refreshSessionPort.findSessionByTokenHash(anyString())).thenReturn(Optional.of(new RefreshSession(
                54L,
                userUuid,
                sha256("refresh-raw-token"),
                "Browser/1.0",
                "old-access-jti",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(12),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10),
                false
        )));
        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("fresh-access-token")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthService service = buildService();
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshResult result = service.refresh("refresh-raw-token", "Browser/1.0");

        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);
        ArgumentCaptor<OffsetDateTime> expiresAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

        assertFalse(result.refreshToken().isBlank());
        assertFalse(result.persistentLogin());
        verify(refreshSessionPort).rotateSession(
                eq(54L),
                anyString(),
                expiresAtCaptor.capture(),
                eq("Browser/1.0"),
                eq(false),
                anyString(),
                any(OffsetDateTime.class)
        );

        OffsetDateTime expiresAt = expiresAtCaptor.getValue();
        assertFalse(expiresAt.isBefore(before.plusSeconds(86400)));
        assertFalse(expiresAt.isAfter(after.plusSeconds(86401)));
    }

    @Test
    void refreshConSesionExpiradaDebeEliminarlaYRechazar() {
        UUID userUuid = UUID.randomUUID();
        when(refreshSessionPort.findSessionByTokenHash(anyString())).thenReturn(Optional.of(new RefreshSession(
                77L,
                userUuid,
                sha256("expired-refresh"),
                "Browser/1.0",
                "expired-jti",
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5),
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                false
        )));

        AuthService service = buildService();

        ApiException ex = assertThrows(ApiException.class, () -> service.refresh("expired-refresh", "Browser/1.0"));

        assertEquals("AUTH_REFRESH_SESSION_INVALID", ex.getCode());
        verify(refreshSessionPort).deleteSessionById(77L);
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void logoutDebeRevocarAccessTokenYEliminarSoloLaSesionActual() {
        UUID userUuid = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .claim("jti", "jti-123")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode("jwt-token")).thenReturn(jwt);
        when(refreshSessionPort.deleteSessionByTokenHash(anyString())).thenReturn(Optional.of(new RefreshSession(
                99L,
                userUuid,
                sha256("refresh-token"),
                "Browser/1.0",
                "jti-123",
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(2),
                true
        )));

        AuthService service = buildService();

        service.logout("jwt-token", "refresh-token");

        verify(tokenRevocationPort).revokeToken(eq("jti-123"), eq(userUuid), any(OffsetDateTime.class));
        verify(refreshSessionPort).deleteSessionByTokenHash(anyString());
        verify(auditLogPort).log(eq("user_logged_out"), eq(userUuid), eq(userUuid), eq(Map.of("jti", "jti-123", "session_id", 99L)));
        verify(outboxService).enqueue(eq("auth"), eq(userUuid), eq("UserLoggedOut"), eq(userUuid), eq(Map.of("jti", "jti-123", "session_id", 99L)));
    }

    @Test
    void getCurrentUserSessionsDebeListarYMarcarLaSesionActual() {
        UUID userUuid = UUID.randomUUID();
        OffsetDateTime currentAccessExpiresAt = OffsetDateTime.parse("2026-06-26T03:19:00Z");
        OffsetDateTime currentSessionExpiresAt = OffsetDateTime.parse("2026-07-03T02:34:00Z");
        OffsetDateTime remoteAccessExpiresAt = OffsetDateTime.parse("2026-06-26T10:15:00Z");
        OffsetDateTime remoteSessionExpiresAt = OffsetDateTime.parse("2026-06-27T09:00:00Z");
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Sesiones",
                "docente.sesiones@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(refreshSessionPort.findSessionsByUserUuid(userUuid)).thenReturn(List.of(
                new RefreshSession(
                        21L,
                        userUuid,
                        sha256("current-refresh-token"),
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        "current-jti",
                        currentAccessExpiresAt,
                        currentSessionExpiresAt,
                        OffsetDateTime.parse("2026-06-26T02:34:00Z"),
                        true
                ),
                new RefreshSession(
                        22L,
                        userUuid,
                        sha256("other-refresh-token"),
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)",
                        "other-jti",
                        remoteAccessExpiresAt,
                        remoteSessionExpiresAt,
                        OffsetDateTime.parse("2026-06-26T00:00:00Z"),
                        false
                )
        ));

        AuthService service = buildService();

        List<CurrentUserSessionSummary> result = service.getCurrentUserSessions(userUuid, "current-refresh-token");

        assertEquals(2, result.size());
        assertEquals("21", result.get(0).id());
        assertTrue(result.get(0).current());
        assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", result.get(0).userAgent());
        assertEquals(currentAccessExpiresAt, result.get(0).accessExpiresAt());
        assertEquals(currentSessionExpiresAt, result.get(0).sessionExpiresAt());
        assertEquals("22", result.get(1).id());
        assertFalse(result.get(1).current());
        assertEquals(remoteAccessExpiresAt, result.get(1).accessExpiresAt());
        assertEquals(remoteSessionExpiresAt, result.get(1).sessionExpiresAt());
    }

    @Test
    void revokeCurrentUserSessionDebeRevocarTokenYEliminarSesionEspecifica() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Sesiones",
                "docente.sesiones@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );
        OffsetDateTime accessExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(35);

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(refreshSessionPort.findSessionByIdAndUserUuid(41L, userUuid)).thenReturn(Optional.of(new RefreshSession(
                41L,
                userUuid,
                sha256("remote-refresh-token"),
                "Mozilla/5.0 (Linux; Android 14)",
                "remote-jti",
                accessExpiresAt,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(5),
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(6),
                true
        )));

        AuthService service = buildService();

        RevokeCurrentUserSessionResult result = service.revokeCurrentUserSession(userUuid, 41L, "current-refresh-token");

        assertFalse(result.currentSessionRevoked());
        verify(tokenRevocationPort).revokeToken("remote-jti", userUuid, accessExpiresAt);
        verify(refreshSessionPort).deleteSessionById(41L);
        verify(auditLogPort).log("user_session_revoked", userUuid, userUuid, Map.of("session_id", 41L, "current", false, "jti", "remote-jti"));
        verify(outboxService).enqueue("auth", userUuid, "UserSessionRevoked", userUuid, Map.of("session_id", 41L, "current", false, "jti", "remote-jti"));
    }

    @Test
    void revokeCurrentUserSessionDebeMarcarCuandoSeRevocaLaSesionActual() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Sesiones",
                "docente.sesiones@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(refreshSessionPort.findSessionByIdAndUserUuid(55L, userUuid)).thenReturn(Optional.of(new RefreshSession(
                55L,
                userUuid,
                sha256("current-refresh-token"),
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)",
                "current-jti",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(55),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(6),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30),
                true
        )));

        AuthService service = buildService();

        RevokeCurrentUserSessionResult result = service.revokeCurrentUserSession(userUuid, 55L, "current-refresh-token");

        assertTrue(result.currentSessionRevoked());
        verify(refreshSessionPort).deleteSessionById(55L);
    }

    @Test
    void revokeCurrentUserSessionSinJtiDebeEliminarLaSesionSinFallar() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Sesiones",
                "docente.sesiones@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(refreshSessionPort.findSessionByIdAndUserUuid(63L, userUuid)).thenReturn(Optional.of(new RefreshSession(
                63L,
                userUuid,
                sha256("legacy-refresh-token"),
                "Legacy UA",
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(2),
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                false
        )));

        AuthService service = buildService();

        RevokeCurrentUserSessionResult result = service.revokeCurrentUserSession(userUuid, 63L, "other-refresh-token");

        assertFalse(result.currentSessionRevoked());
        verify(refreshSessionPort).deleteSessionById(63L);
        verify(tokenRevocationPort, never()).revokeToken(anyString(), eq(userUuid), any(OffsetDateTime.class));
    }

    @Test
    void revokeCurrentUserSessionAjenaDebeRetornar404() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Sesiones",
                "docente.sesiones@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(refreshSessionPort.findSessionByIdAndUserUuid(77L, userUuid)).thenReturn(Optional.empty());

        AuthService service = buildService();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.revokeCurrentUserSession(userUuid, 77L, "current-refresh-token")
        );

        assertEquals("AUTH_SESSION_NOT_FOUND", ex.getCode());
        verify(refreshSessionPort, never()).deleteSessionById(77L);
    }

    @Test
    void logoutSinTokensDebeSerIdempotente() {
        AuthService service = buildService();

        service.logout(null, null);

        verifyNoInteractions(tokenRevocationPort, refreshSessionPort, auditLogPort, outboxService);
    }

    @Test
    void getCurrentUserDebeRetornarResumenNormalizado() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Coordinadora Perfil",
                "perfil@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "coordinador",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));

        AuthService service = buildService();

        AuthenticatedUserSummary result = service.getCurrentUser(userUuid);

        assertEquals(userUuid, result.id());
        assertEquals("Coordinadora Perfil", result.name());
        assertEquals("perfil@panol.test", result.email());
        assertEquals("COORDINADOR", result.role());
    }

    @Test
    void issueBotAccessTokenDebeEmitirJwtConAudienceDelBot() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Coordinadora Bot",
                "bot@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "coordinador",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("bot-bridge-token")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .claim("aud", List.of("bot-panol"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        AuthService service = buildService();

        BotAccessTokenResult result = service.issueBotAccessToken(userUuid);

        assertEquals("bot-bridge-token", result.token());
        assertEquals(300, result.expiresInSeconds());
        verify(jwtEncoder).encode(any(JwtEncoderParameters.class));
    }

    @Test
    void issueBotAccessTokenDebeRechazarRolesNoPermitidos() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Bot",
                "docente.bot@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));

        AuthService service = buildService();

        ApiException ex = assertThrows(ApiException.class, () -> service.issueBotAccessToken(userUuid));

        assertEquals("AUTH_BOT_TOKEN_FORBIDDEN", ex.getCode());
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void updateCurrentUserEmailDebeActualizarYRegistrarEvento() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Perfil",
                "docente@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(userAuthPort.existsOtherUserWithEmail("docente.nuevo@panol.test", userUuid)).thenReturn(false);

        AuthService service = buildService();

        AuthenticatedUserSummary result = service.updateCurrentUserEmail(userUuid, new UpdateCurrentEmailCommand("DOCENTE.NUEVO@panol.test"));

        assertEquals("docente.nuevo@panol.test", result.email());
        verify(userAuthPort).updateEmail(userUuid, "docente.nuevo@panol.test");
        verify(auditLogPort).log("user_email_changed", userUuid, userUuid, Map.of("email", "docente.nuevo@panol.test"));
        verify(outboxService).enqueue("user", userUuid, "UserEmailChanged", userUuid, Map.of("email", "docente.nuevo@panol.test"));
    }

    @Test
    void updateCurrentUserEmailDebeRechazarDuplicados() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Perfil",
                "docente@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));
        when(userAuthPort.existsOtherUserWithEmail("ocupado@panol.test", userUuid)).thenReturn(true);

        AuthService service = buildService();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.updateCurrentUserEmail(userUuid, new UpdateCurrentEmailCommand("ocupado@panol.test"))
        );

        assertEquals("AUTH_EMAIL_ALREADY_IN_USE", ex.getCode());
        verify(userAuthPort, never()).updateEmail(any(), any());
    }

    @Test
    void updateCurrentUserPasswordDebeActualizarHashYRegistrarEvento() {
        UUID userUuid = UUID.randomUUID();
        String currentHash = BCrypt.hashpw("secret123", BCrypt.gensalt());
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Perfil",
                "docente@panol.test",
                currentHash,
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));

        AuthService service = buildService();

        service.updateCurrentUserPassword(userUuid, new ChangeCurrentPasswordCommand("secret123", "nuevo1234"));

        verify(userAuthPort).updatePasswordHash(eq(userUuid), any(String.class));
        verify(auditLogPort).log("user_password_changed", userUuid, userUuid, Map.of("source", "self_service"));
        verify(outboxService).enqueue("user", userUuid, "UserPasswordChanged", userUuid, Map.of("source", "self_service"));
    }

    @Test
    void updateCurrentUserPasswordDebeRechazarContrasenaActualIncorrecta() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678",
                "Docente Perfil",
                "docente@panol.test",
                BCrypt.hashpw("secret123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );

        when(userAuthPort.findAuthUserByUuid(userUuid)).thenReturn(Optional.of(authUser));

        AuthService service = buildService();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.updateCurrentUserPassword(userUuid, new ChangeCurrentPasswordCommand("otra", "nuevo1234"))
        );

        assertEquals("AUTH_CURRENT_PASSWORD_INVALID", ex.getCode());
        verify(userAuthPort, never()).updatePasswordHash(any(), any());
    }

    @Test
    void requestPasswordRecoveryDebeCrearSolicitudYEncolarCorreo() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678K",
                "Ana Perez",
                "ana.perez@panol.test",
                BCrypt.hashpw("Panol123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );
        ArgumentCaptor<String> emailedCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> storedCodeHashCaptor = ArgumentCaptor.forClass(String.class);

        when(passwordRecoveryNotificationPort.isAvailable()).thenReturn(true);
        when(userAuthPort.findAuthUserByRut("12345678K")).thenReturn(Optional.of(authUser));
        when(userAuthPort.findLatestPasswordRecoveryRequestByUserUuid(userUuid)).thenReturn(Optional.empty());

        AuthService service = buildService();

        service.requestPasswordRecovery(new PasswordRecoveryRequestCommand("12.345.678-K"));

        verify(userAuthPort).invalidatePasswordRecoveryRequests(eq(userUuid), any(OffsetDateTime.class));
        verify(userAuthPort).createPasswordRecoveryRequest(
                eq(userUuid),
                storedCodeHashCaptor.capture(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        );
        verify(passwordRecoveryNotificationPort).enqueuePasswordRecoveryEmail(
                eq("Ana Perez"),
                eq("ana.perez@panol.test"),
                eq("12345678K"),
                emailedCodeCaptor.capture(),
                eq(15)
        );
        verify(auditLogPort).log("user_password_recovery_requested", userUuid, userUuid, Map.of("rut", "12345678K"));
        verify(outboxService).enqueue("auth", userUuid, "UserPasswordRecoveryRequested", userUuid, Map.of("rut", "12345678K"));

        String emailedCode = emailedCodeCaptor.getValue();
        assertEquals(8, emailedCode.length());
        assertTrue(emailedCode.matches("[A-Z0-9]{8}"));
        assertEquals(sha256(emailedCode), storedCodeHashCaptor.getValue());
    }

    @Test
    void requestPasswordRecoveryDebeIgnorarCooldownActivoSinFiltrarExistencia() {
        UUID userUuid = UUID.randomUUID();
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678K",
                "Ana Perez",
                "ana.perez@panol.test",
                BCrypt.hashpw("Panol123", BCrypt.gensalt()),
                "DOCENTE",
                0,
                null
        );
        PasswordRecoveryRequestRecord activeRequest = new PasswordRecoveryRequestRecord(
                1L,
                userUuid,
                "Ana Perez",
                "ana.perez@panol.test",
                "12345678K",
                authUser.passwordHash(),
                sha256("AB12CD34"),
                null,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30),
                0,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30),
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30)
        );

        when(passwordRecoveryNotificationPort.isAvailable()).thenReturn(true);
        when(userAuthPort.findAuthUserByRut("12345678K")).thenReturn(Optional.of(authUser));
        when(userAuthPort.findLatestPasswordRecoveryRequestByUserUuid(userUuid)).thenReturn(Optional.of(activeRequest));

        AuthService service = buildService();

        service.requestPasswordRecovery(new PasswordRecoveryRequestCommand("12.345.678-K"));

        verify(userAuthPort, never()).createPasswordRecoveryRequest(any(), any(), any(), any());
        verify(passwordRecoveryNotificationPort, never()).enqueuePasswordRecoveryEmail(any(), any(), any(), any(), anyInt());
    }

    @Test
    void verifyPasswordRecoveryCodeDebeEmitirResetTokenCuandoElCodigoEsValido() {
        UUID userUuid = UUID.randomUUID();
        String currentPasswordHash = BCrypt.hashpw("Panol123", BCrypt.gensalt());
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678K",
                "Ana Perez",
                "ana.perez@panol.test",
                currentPasswordHash,
                "DOCENTE",
                0,
                null
        );
        PasswordRecoveryRequestRecord requestRecord = new PasswordRecoveryRequestRecord(
                10L,
                userUuid,
                "Ana Perez",
                "ana.perez@panol.test",
                "12345678K",
                currentPasswordHash,
                sha256("AB12CD34"),
                null,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(20),
                0,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );

        when(userAuthPort.findAuthUserByRut("12345678K")).thenReturn(Optional.of(authUser));
        when(userAuthPort.findLatestPasswordRecoveryRequestByUserUuid(userUuid)).thenReturn(Optional.of(requestRecord));

        AuthService service = buildService();

        PasswordRecoveryVerificationResult result = service.verifyPasswordRecoveryCode(
                new PasswordRecoveryVerifyCommand("12.345.678-K", "ab12cd34")
        );

        assertFalse(result.resetToken().isBlank());
        assertEquals(600, result.expiresInSeconds());
        verify(userAuthPort).verifyPasswordRecoveryRequest(eq(10L), anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class));
        verify(auditLogPort).log("user_password_recovery_verified", userUuid, userUuid, Map.of("rut", "12345678K"));
        verify(outboxService).enqueue("auth", userUuid, "UserPasswordRecoveryVerified", userUuid, Map.of("rut", "12345678K"));
    }

    @Test
    void verifyPasswordRecoveryCodeDebeInvalidarseAlQuintoIntentoFallido() {
        UUID userUuid = UUID.randomUUID();
        String currentPasswordHash = BCrypt.hashpw("Panol123", BCrypt.gensalt());
        AuthUser authUser = new AuthUser(
                userUuid,
                "12345678K",
                "Ana Perez",
                "ana.perez@panol.test",
                currentPasswordHash,
                "DOCENTE",
                0,
                null
        );
        PasswordRecoveryRequestRecord requestRecord = new PasswordRecoveryRequestRecord(
                12L,
                userUuid,
                "Ana Perez",
                "ana.perez@panol.test",
                "12345678K",
                currentPasswordHash,
                sha256("AB12CD34"),
                null,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(20),
                4,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );

        when(userAuthPort.findAuthUserByRut("12345678K")).thenReturn(Optional.of(authUser));
        when(userAuthPort.findLatestPasswordRecoveryRequestByUserUuid(userUuid)).thenReturn(Optional.of(requestRecord));

        AuthService service = buildService();

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.verifyPasswordRecoveryCode(new PasswordRecoveryVerifyCommand("12.345.678-K", "ZZ99ZZ99"))
        );

        assertEquals("AUTH_PASSWORD_RECOVERY_CODE_ATTEMPTS_EXCEEDED", ex.getCode());
        verify(userAuthPort).incrementPasswordRecoveryAttempt(eq(12L), eq(5), any(OffsetDateTime.class));
        verify(userAuthPort).consumePasswordRecoveryRequest(eq(12L), any(OffsetDateTime.class));
    }

    @Test
    void resetPasswordFromRecoveryDebeActualizarHashYRevocarSesionesActivas() {
        UUID userUuid = UUID.randomUUID();
        String currentPasswordHash = BCrypt.hashpw("Panol123", BCrypt.gensalt());
        PasswordRecoveryRequestRecord requestRecord = new PasswordRecoveryRequestRecord(
                18L,
                userUuid,
                "Ana Perez",
                "ana.perez@panol.test",
                "12345678K",
                currentPasswordHash,
                sha256("AB12CD34"),
                sha256("reset-token"),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30),
                null,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2),
                1,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2),
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30)
        );

        when(userAuthPort.findPasswordRecoveryRequestByResetTokenHash(sha256("reset-token"))).thenReturn(Optional.of(requestRecord));
        when(refreshSessionPort.deleteSessionsByUserUuid(userUuid)).thenReturn(List.of(
                new RefreshSession(
                        71L,
                        userUuid,
                        sha256("refresh-a"),
                        "Browser A",
                        "jti-a",
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                        true
                ),
                new RefreshSession(
                        72L,
                        userUuid,
                        sha256("refresh-b"),
                        "Browser B",
                        null,
                        null,
                        OffsetDateTime.now(ZoneOffset.UTC).plusHours(10),
                        OffsetDateTime.now(ZoneOffset.UTC).minusHours(3),
                        false
                )
        ));

        AuthService service = buildService();

        service.resetPasswordFromRecovery(new PasswordRecoveryResetCommand("reset-token", "Nueva1234"));

        verify(userAuthPort).updatePasswordHash(eq(userUuid), anyString());
        verify(refreshSessionPort).deleteSessionsByUserUuid(userUuid);
        verify(tokenRevocationPort).revokeToken(eq("jti-a"), eq(userUuid), any(OffsetDateTime.class));
        verify(userAuthPort).consumePasswordRecoveryRequest(eq(18L), any(OffsetDateTime.class));
        verify(auditLogPort).log("user_password_changed", userUuid, userUuid, Map.of("source", "password_recovery"));
        verify(outboxService).enqueue("user", userUuid, "UserPasswordChanged", userUuid, Map.of("source", "password_recovery"));
    }

    private AuthService buildService() {
        return new AuthService(
                userAuthPort,
                refreshSessionPort,
                tokenRevocationPort,
                passwordRecoveryNotificationPort,
                jwtEncoder,
                jwtDecoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                604800,
                86400,
                8,
                15,
                5,
                120,
                600,
                "panol-backend",
                300,
                "bot-panol"
        );
    }

    private String sha256(String rawValue) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(rawValue.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

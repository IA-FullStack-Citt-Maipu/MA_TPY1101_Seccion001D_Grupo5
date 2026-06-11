package com.panol_project.backendpanol.modules.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.auth.application.dto.LoginCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginResult;
import com.panol_project.backendpanol.modules.auth.application.dto.ChangeCurrentPasswordCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.UpdateCurrentEmailCommand;
import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import com.panol_project.backendpanol.modules.auth.domain.AuthUser;
import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import com.panol_project.backendpanol.modules.auth.domain.UserAuthPort;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import com.panol_project.backendpanol.shared.error.ApiException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAuthPort userAuthPort;

    @Mock
    private TokenRevocationPort tokenRevocationPort;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private OutboxService outboxService;

    @Test
    void loginDebeRetornarResultadoDeAplicacionYRegistrarEventos() {
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

        when(userAuthPort.findAuthUserByRut("12345678")).thenReturn(Optional.of(authUser));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("token-123")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

        LoginResult result = service.login(new LoginCommand("12.345.678-9", "secret"));

        assertEquals("token-123", result.accessToken());
        assertEquals("DIRECTOR", result.role());
        assertEquals(3600, result.expiresInSeconds());
        assertEquals("Directora Prueba", result.user().name());
        assertEquals("directora@panol.test", result.user().email());
        assertEquals("DIRECTOR", result.user().role());
        verify(userAuthPort).resetLoginAttempts(eq(userUuid), any(OffsetDateTime.class));
        verify(auditLogPort).log("user_logged_in", userUuid, userUuid, Map.of("rut", "12345678", "role", "DIRECTOR"));
        verify(outboxService).enqueue("user", userUuid, "UserLoggedIn", userUuid, Map.of("rut", "12345678", "role", "DIRECTOR"));
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

        when(userAuthPort.findAuthUserByRut("12345678")).thenReturn(Optional.of(authUser));

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.login(new LoginCommand("12.345.678-9", "wrong-pass"))
        );

        assertEquals("AUTH_INVALID_CREDENTIALS", ex.getCode());
        verify(userAuthPort).registerFailedAttempt(eq(userUuid), eq(1), eq(null));
        verify(auditLogPort).log("login_failed", null, null, Map.of("rut", "12345678"));
        verify(outboxService).enqueue("auth", null, "LoginFailed", null, Map.of("rut", "12345678"));
    }

    @Test
    void logoutDebeRevocarTokenYRegistrarEvento() {
        UUID userUuid = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .claim("jti", "jti-123")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

        service.logout(jwt);

        verify(tokenRevocationPort).revokeToken(
                eq("jti-123"),
                eq(userUuid),
                any(OffsetDateTime.class)
        );
        verify(auditLogPort).log("user_logged_out", null, null, Map.of("jti", "jti-123"));
        verify(outboxService).enqueue("auth", userUuid, "UserLoggedOut", userUuid, Map.of("jti", "jti-123"));
    }

    @Test
    void logoutSinJtiDebeRechazarToken() {
        UUID userUuid = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.logout(jwt));
        assertEquals("AUTH_JTI_MISSING", ex.getCode());
        verify(tokenRevocationPort, never()).revokeToken(any(), any(), any());
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

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

        var result = service.getCurrentUser(userUuid);

        assertEquals(userUuid, result.id());
        assertEquals("Coordinadora Perfil", result.name());
        assertEquals("perfil@panol.test", result.email());
        assertEquals("COORDINADOR", result.role());
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

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

        var result = service.updateCurrentUserEmail(userUuid, new UpdateCurrentEmailCommand("DOCENTE.NUEVO@panol.test"));

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

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

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

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

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

        AuthService service = new AuthService(
                userAuthPort,
                tokenRevocationPort,
                jwtEncoder,
                auditLogPort,
                outboxService,
                5,
                15,
                3600,
                "panol-backend"
        );

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.updateCurrentUserPassword(userUuid, new ChangeCurrentPasswordCommand("otra", "nuevo1234"))
        );

        assertEquals("AUTH_CURRENT_PASSWORD_INVALID", ex.getCode());
        verify(userAuthPort, never()).updatePasswordHash(any(), any());
    }
}

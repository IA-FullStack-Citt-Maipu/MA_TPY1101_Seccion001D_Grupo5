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
}

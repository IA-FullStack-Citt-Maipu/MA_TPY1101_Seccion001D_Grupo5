package com.panol_project.backendpanol.modules.auth.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.panol_project.backendpanol.modules.auth.application.AuthService;
import com.panol_project.backendpanol.modules.auth.application.dto.AuthenticatedUserSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.BotAccessTokenResult;
import com.panol_project.backendpanol.modules.auth.application.dto.ChangeCurrentPasswordCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.CurrentUserSessionSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginResult;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryRequestCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryResetCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryVerificationResult;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryVerifyCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.RefreshResult;
import com.panol_project.backendpanol.modules.auth.application.dto.RevokeCurrentUserSessionResult;
import com.panol_project.backendpanol.modules.auth.application.dto.UpdateCurrentEmailCommand;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthV2Controller.class)
@Import({
        AuthV2ControllerTest.TestSecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AuthV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void loginDebeSetearCookiesHttpOnlyYRetornarPayloadPublico() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.login(eq(new com.panol_project.backendpanol.modules.auth.application.dto.LoginCommand(
                "22307980",
                "Panol123",
                true,
                "JUnit"
        )))).thenReturn(new LoginResult(
                "access-token",
                "refresh-token",
                "DOCENTE",
                3600,
                new AuthenticatedUserSummary(userUuid, "Carla Soto", "carla.docente@panol.local", "DOCENTE"),
                true
        ));

        mockMvc.perform(post("/api/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit")
                        .content("""
                                {
                                  "rut": "22307980",
                                  "password": "Panol123",
                                  "rememberMe": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCENTE"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.user.id").value(userUuid.toString()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("panol_access_token=access-token")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(2, result.getResponse().getHeaders("Set-Cookie").size()));
    }

    @Test
    void refreshDebeRotarCookiesYRetornar204() throws Exception {
        when(authService.refresh("refresh-cookie", "JUnit")).thenReturn(new RefreshResult(
                "new-access-token",
                "new-refresh-token",
                false
        ));

        mockMvc.perform(post("/api/v2/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-cookie"))
                        .header("User-Agent", "JUnit"))
                .andExpect(status().isNoContent())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(2, result.getResponse().getHeaders("Set-Cookie").size()));

        verify(authService).refresh("refresh-cookie", "JUnit");
    }

    @Test
    void requestPasswordRecoveryDebeResponder202SinAutenticacion() throws Exception {
        PasswordRecoveryRequestCommand command = new PasswordRecoveryRequestCommand("12345678K");

        mockMvc.perform(post("/api/v2/auth/password-recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rut": "12345678K"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(authService).requestPasswordRecovery(eq(command));
    }

    @Test
    void verifyPasswordRecoveryDebeRetornarResetToken() throws Exception {
        PasswordRecoveryVerifyCommand command = new PasswordRecoveryVerifyCommand("12345678K", "AB12CD34");
        when(authService.verifyPasswordRecoveryCode(eq(command)))
                .thenReturn(new PasswordRecoveryVerificationResult("opaque-reset-token", 600));

        mockMvc.perform(post("/api/v2/auth/password-recovery/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rut": "12345678K",
                                  "code": "AB12CD34"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset_token").value("opaque-reset-token"))
                .andExpect(jsonPath("$.expires_in_seconds").value(600));

        verify(authService).verifyPasswordRecoveryCode(eq(command));
    }

    @Test
    void resetPasswordRecoveryDebeRetornar204() throws Exception {
        PasswordRecoveryResetCommand command = new PasswordRecoveryResetCommand("opaque-reset-token", "Nueva1234");

        mockMvc.perform(post("/api/v2/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reset_token": "opaque-reset-token",
                                  "new_password": "Nueva1234"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(authService).resetPasswordFromRecovery(eq(command));
    }

    @Test
    void logoutDebeExpirarCookiesYDelegarTokensCrudos() throws Exception {
        mockMvc.perform(post("/api/v2/auth/logout")
                        .cookie(
                                new Cookie(AuthCookieService.ACCESS_COOKIE_NAME, "access-cookie"),
                                new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-cookie")
                        ))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookieService.ACCESS_COOKIE_NAME, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_COOKIE_NAME, 0));

        verify(authService).logout("access-cookie", "refresh-cookie");
    }

    @Test
    void getCurrentUserDebeRetornarResumenDelUsuarioAutenticado() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.getCurrentUser(userUuid)).thenReturn(new AuthenticatedUserSummary(
                userUuid,
                "Carla Soto",
                "carla.docente@panol.local",
                "DOCENTE"
        ));

        mockMvc.perform(get("/api/v2/auth/me")
                        .with(authentication(jwtAuthentication(userUuid, "DOCENTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userUuid.toString()))
                .andExpect(jsonPath("$.name").value("Carla Soto"))
                .andExpect(jsonPath("$.email").value("carla.docente@panol.local"))
                .andExpect(jsonPath("$.role").value("DOCENTE"));

        verify(authService).getCurrentUser(userUuid);
    }

    @Test
    void issueBotAccessTokenDebeRetornarPayloadEfimeroParaRolesPermitidos() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.issueBotAccessToken(userUuid)).thenReturn(new BotAccessTokenResult(
                "bridge-token",
                300
        ));

        mockMvc.perform(post("/api/v2/auth/me/bot-token")
                        .with(authentication(jwtAuthentication(userUuid, "COORDINADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("bridge-token"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));

        verify(authService).issueBotAccessToken(userUuid);
    }

    @Test
    void issueBotAccessTokenDebeRechazarDocente() throws Exception {
        UUID userUuid = UUID.randomUUID();

        mockMvc.perform(post("/api/v2/auth/me/bot-token")
                        .with(authentication(jwtAuthentication(userUuid, "DOCENTE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(authService);
    }

    @Test
    void getCurrentUserSessionsDebeRetornarListaDelUsuarioActual() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.getCurrentUserSessions(userUuid, "refresh-cookie")).thenReturn(List.of(
                new CurrentUserSessionSummary(
                        "41",
                        true,
                        true,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        OffsetDateTime.parse("2026-06-13T15:00:00Z"),
                        OffsetDateTime.parse("2026-06-13T16:00:00Z"),
                        OffsetDateTime.parse("2026-06-20T15:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/v2/auth/me/sessions")
                        .with(authentication(jwtAuthentication(userUuid, "DOCENTE")))
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-cookie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("41"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].persistentLogin").value(true))
                .andExpect(jsonPath("$[0].userAgent").value("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                .andExpect(jsonPath("$[0].accessExpiresAt").value("2026-06-13T16:00:00Z"))
                .andExpect(jsonPath("$[0].sessionExpiresAt").value("2026-06-20T15:00:00Z"));

        verify(authService).getCurrentUserSessions(userUuid, "refresh-cookie");
    }

    @Test
    void revokeCurrentUserSessionDebeExpirarCookiesSiEsLaSesionActual() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.revokeCurrentUserSession(userUuid, 41L, "refresh-cookie"))
                .thenReturn(new RevokeCurrentUserSessionResult(true));

        mockMvc.perform(delete("/api/v2/auth/me/sessions/41")
                        .with(authentication(jwtAuthentication(userUuid, "DOCENTE")))
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-cookie")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookieService.ACCESS_COOKIE_NAME, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_COOKIE_NAME, 0));

        verify(authService).revokeCurrentUserSession(userUuid, 41L, "refresh-cookie");
    }

    @Test
    void revokeCurrentUserSessionRemotaDebeRetornar204SinExpirarCookies() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.revokeCurrentUserSession(userUuid, 52L, "refresh-cookie"))
                .thenReturn(new RevokeCurrentUserSessionResult(false));

        mockMvc.perform(delete("/api/v2/auth/me/sessions/52")
                        .with(authentication(jwtAuthentication(userUuid, "DOCENTE")))
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-cookie")))
                .andExpect(status().isNoContent())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getHeaders("Set-Cookie").isEmpty()
                ));

        verify(authService).revokeCurrentUserSession(userUuid, 52L, "refresh-cookie");
    }

    @Test
    void updateCurrentUserEmailDebeMapearPayloadYRetornarResumenActualizado() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UpdateCurrentEmailCommand command = new UpdateCurrentEmailCommand("carla.docente+settings@panol.local");
        when(authService.updateCurrentUserEmail(eq(userUuid), eq(command))).thenReturn(new AuthenticatedUserSummary(
                userUuid,
                "Carla Soto",
                "carla.docente+settings@panol.local",
                "DOCENTE"
        ));

        mockMvc.perform(patch("/api/v2/auth/me/email")
                        .with(authentication(jwtAuthentication(userUuid, "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "carla.docente+settings@panol.local"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carla.docente+settings@panol.local"));

        verify(authService).updateCurrentUserEmail(eq(userUuid), eq(command));
    }

    @Test
    void updateCurrentUserPasswordDebeAceptarSnakeCaseYRetornar204() throws Exception {
        UUID userUuid = UUID.randomUUID();
        ChangeCurrentPasswordCommand command = new ChangeCurrentPasswordCommand("Panol123", "Panol456");

        mockMvc.perform(patch("/api/v2/auth/me/password")
                        .with(authentication(jwtAuthentication(userUuid, "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "current_password": "Panol123",
                                  "new_password": "Panol456"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(authService).updateCurrentUserPassword(eq(userUuid), eq(command));
    }

    @Test
    void getCurrentUserDebeRetornar401SinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v2/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("No autorizado"));

        verifyNoInteractions(authService);
    }

    private Authentication jwtAuthentication(UUID userUuid, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userUuid.toString())
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        CurrentUserUuidResolver currentUserUuidResolver() {
            return new CurrentUserUuidResolver();
        }

        @Bean
        AuthCookieService authCookieService() {
            return new AuthCookieService(3600, 604800, false, "Lax");
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                RestAuthenticationEntryPoint authenticationEntryPoint,
                RestAccessDeniedHandler accessDeniedHandler
        ) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/api/v2/auth/login",
                                    "/api/v2/auth/logout",
                                    "/api/v2/auth/refresh",
                                    "/api/v2/auth/password-recovery/request",
                                    "/api/v2/auth/password-recovery/verify",
                                    "/api/v2/auth/password-recovery/reset"
                            ).permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .build();
        }
    }
}

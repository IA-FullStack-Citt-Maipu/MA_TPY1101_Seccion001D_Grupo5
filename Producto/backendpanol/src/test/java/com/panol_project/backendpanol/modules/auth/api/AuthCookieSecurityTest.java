package com.panol_project.backendpanol.modules.auth.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.panol_project.backendpanol.bootstrap.config.CorsConfig;
import com.panol_project.backendpanol.bootstrap.config.SecurityConfig;
import com.panol_project.backendpanol.modules.auth.application.AuthService;
import com.panol_project.backendpanol.modules.auth.application.dto.AuthenticatedUserSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.CurrentUserSessionSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.RevokeCurrentUserSessionResult;
import com.panol_project.backendpanol.modules.auth.infrastructure.TokenRevocationValidator;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import com.panol_project.backendpanol.shared.security.CookieOrHeaderBearerTokenResolver;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthV2Controller.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        AuthCookieService.class,
        CookieOrHeaderBearerTokenResolver.class,
        CurrentUserUuidResolver.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "app.auth.jwt.secret=test-secret-key-change-in-prod-12345678901234567890",
        "app.auth.cookies.secure=false",
        "app.auth.cookies.same-site=Lax"
})
class AuthCookieSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private AuthService authService;

    @MockBean
    private TokenRevocationValidator tokenRevocationValidator;

    @BeforeEach
    void setUp() {
        when(tokenRevocationValidator.validate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(OAuth2TokenValidatorResult.success());
    }

    @Test
    void getCurrentUserDebeAutenticarUsandoCookieAccessToken() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.getCurrentUser(eq(userUuid))).thenReturn(new AuthenticatedUserSummary(
                userUuid,
                "Carla Soto",
                "carla.docente@panol.local",
                "DOCENTE"
        ));

        mockMvc.perform(get("/api/v2/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie(AuthCookieService.ACCESS_COOKIE_NAME, createToken(userUuid, "DOCENTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userUuid.toString()))
                .andExpect(jsonPath("$.role").value("DOCENTE"));
    }

    @Test
    void getCurrentUserSessionsDebeAutenticarUsandoCookieAccessToken() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.getCurrentUserSessions(eq(userUuid), eq("refresh-cookie"))).thenReturn(List.of(
                new CurrentUserSessionSummary(
                        "41",
                        true,
                        true,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        OffsetDateTime.parse("2026-06-13T12:00:00Z"),
                        OffsetDateTime.parse("2026-06-13T13:00:00Z"),
                        OffsetDateTime.parse("2026-06-20T12:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/v2/auth/me/sessions")
                        .cookie(
                                new jakarta.servlet.http.Cookie(AuthCookieService.ACCESS_COOKIE_NAME, createToken(userUuid, "DOCENTE")),
                                new jakarta.servlet.http.Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-cookie")
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("41"))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void deleteCurrentUserSessionDebeAutenticarUsandoCookieAccessToken() throws Exception {
        UUID userUuid = UUID.randomUUID();
        when(authService.revokeCurrentUserSession(eq(userUuid), eq(41L), eq("refresh-cookie")))
                .thenReturn(new RevokeCurrentUserSessionResult(false));

        mockMvc.perform(delete("/api/v2/auth/me/sessions/41")
                        .cookie(
                                new jakarta.servlet.http.Cookie(AuthCookieService.ACCESS_COOKIE_NAME, createToken(userUuid, "DOCENTE")),
                                new jakarta.servlet.http.Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-cookie")
                        ))
                .andExpect(status().isNoContent());
    }

    @Test
    void corsDebePermitirCredencialesEnPreflight() throws Exception {
        mockMvc.perform(options("/api/v2/auth/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private String createToken(UUID userUuid, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userUuid.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .id(UUID.randomUUID().toString())
                .claim("role", role)
                .build();
        return jwtEncoder.encode(
                        JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}

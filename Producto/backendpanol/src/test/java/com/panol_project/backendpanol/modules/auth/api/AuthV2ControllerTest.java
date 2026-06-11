package com.panol_project.backendpanol.modules.auth.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.panol_project.backendpanol.modules.auth.application.AuthService;
import com.panol_project.backendpanol.modules.auth.application.dto.AuthenticatedUserSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.ChangeCurrentPasswordCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.UpdateCurrentEmailCommand;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
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
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                RestAuthenticationEntryPoint authenticationEntryPoint,
                RestAccessDeniedHandler accessDeniedHandler
        ) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .build();
        }
    }
}

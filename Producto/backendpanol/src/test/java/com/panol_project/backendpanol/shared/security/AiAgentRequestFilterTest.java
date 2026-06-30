package com.panol_project.backendpanol.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TestAiAgentEchoController.class)
@Import({
        AiAgentRequestFilterTest.TestSecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AiAgentRequestFilterTest {

    private static final String SECRET = "test-ai-agent-secret";
    private static final String BASE_PATH = "/api/v2/test-ai-agent";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestSinOrigenAiAgentNoDebeSerBloqueadaPorElFiltro() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR"))))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void requestConOrigenAiAgentYSecretCorrectoDebePermitirGet() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR")))
                        .header("X-Client-Origin", "AI-Agent")
                        .header("X-Client-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void botTokenSinOrigenAiAgentDebeRetornar403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR")))
                        .header("Authorization", "Bearer " + unsignedBotToken("bot-panol")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_AGENT_TOKEN_SCOPE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("El token del asistente solo puede usarse desde AI-Agent."));
    }

    @Test
    void botTokenConOrigenAiAgentYSecretCorrectoDebePermitirGet() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR")))
                        .header("Authorization", "Bearer " + unsignedBotToken("bot-panol"))
                        .header("X-Client-Origin", "AI-Agent")
                        .header("X-Client-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void tokenWebNormalSinTokenUseNoDebeActivarFiltroAiAgent() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR")))
                        .header("Authorization", "Bearer " + unsignedWebToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void requestConOrigenAiAgentYSinSecretDebeRetornar403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR")))
                        .header("X-Client-Origin", "AI-Agent"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_AGENT_SECRET_INVALID"))
                .andExpect(jsonPath("$.message").value("Credenciales de origen AI-Agent invalidas."));
    }

    @Test
    void requestConOrigenAiAgentYSecretIncorrectoDebeRetornar403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR")))
                        .header("X-Client-Origin", "AI-Agent")
                        .header("X-Client-Secret", "otro-secret"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_AGENT_SECRET_INVALID"))
                .andExpect(jsonPath("$.message").value("Credenciales de origen AI-Agent invalidas."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void requestConOrigenAiAgentYMetodoMutadorDebeRetornar403(String method) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), BASE_PATH)
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR")))
                        .header("X-Client-Origin", "AI-Agent")
                        .header("X-Client-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_AGENT_MUTATION_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("El origen AI-Agent solo tiene permisos de lectura."));
    }

    private Authentication jwtAuthentication(UUID userUuid, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userUuid.toString())
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private String unsignedBotToken(String audience) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("""
                        {"sub":"%s","role":"COORDINADOR","token_use":"bot-panol","aud":["%s"]}
                        """.formatted(UUID.randomUUID(), audience)).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    private String unsignedWebToken() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("""
                        {"sub":"%s","role":"COORDINADOR"}
                        """.formatted(UUID.randomUUID())).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        AiAgentRequestFilter aiAgentRequestFilter(ObjectMapper objectMapper) {
            return new AiAgentRequestFilter(objectMapper, SECRET);
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                AiAgentRequestFilter aiAgentRequestFilter,
                RestAuthenticationEntryPoint authenticationEntryPoint,
                RestAccessDeniedHandler accessDeniedHandler
        ) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .addFilterBefore(aiAgentRequestFilter, AuthorizationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .build();
        }
    }
}

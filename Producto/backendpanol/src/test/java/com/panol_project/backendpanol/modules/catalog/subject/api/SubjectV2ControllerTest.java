package com.panol_project.backendpanol.modules.catalog.subject.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.panol_project.backendpanol.modules.catalog.subject.application.SubjectService;
import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectOption;
import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectRepository;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubjectV2Controller.class)
@Import({
        SubjectV2ControllerTest.TestSecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class SubjectV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubjectRepository subjectRepository;

    @ParameterizedTest
    @ValueSource(strings = {"DOCENTE", "COORDINADOR", "DIRECTOR"})
    void listarAsignaturasDebePermitirCualquierRolAutenticado(String role) throws Exception {
        UUID subjectUuid = UUID.randomUUID();
        when(subjectRepository.findAllActive()).thenReturn(List.of(new SubjectOption(11L, subjectUuid, "MAT-101", "Matematica")));

        mockMvc.perform(get("/api/v2/subjects")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11L))
                .andExpect(jsonPath("$[0].uuid").value(subjectUuid.toString()))
                .andExpect(jsonPath("$[0].code").value("MAT-101"))
                .andExpect(jsonPath("$[0].name").value("Matematica"));

        verify(subjectRepository).findAllActive();
    }

    @Test
    void listarAsignaturasDebeRetornar401SinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v2/subjects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("No autorizado"));

        verifyNoInteractions(subjectRepository);
    }

    @Test
    void listarAsignaturasDebeRetornarArregloVacioCuandoNoHayResultados() throws Exception {
        when(subjectRepository.findAllActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/v2/subjects")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(subjectRepository).findAllActive();
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
        SubjectService subjectService(SubjectRepository subjectRepository) {
            return new SubjectService(subjectRepository);
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

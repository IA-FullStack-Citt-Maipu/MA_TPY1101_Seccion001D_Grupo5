package com.panol_project.backendpanol.modules.catalog.room.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.panol_project.backendpanol.modules.catalog.room.application.RoomService;
import com.panol_project.backendpanol.modules.catalog.room.domain.RoomOption;
import com.panol_project.backendpanol.modules.catalog.room.domain.RoomRepository;
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

@WebMvcTest(RoomV2Controller.class)
@Import({
        RoomV2ControllerTest.TestSecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class RoomV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomRepository roomRepository;

    @ParameterizedTest
    @ValueSource(strings = {"DOCENTE", "COORDINADOR", "DIRECTOR"})
    void listarSalasDebePermitirCualquierRolAutenticado(String role) throws Exception {
        UUID roomUuid = UUID.randomUUID();
        when(roomRepository.findAllActive()).thenReturn(List.of(new RoomOption(10L, roomUuid, "Sala 301")));

        mockMvc.perform(get("/api/v2/rooms")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].uuid").value(roomUuid.toString()))
                .andExpect(jsonPath("$[0].name").value("Sala 301"));

        verify(roomRepository).findAllActive();
    }

    @Test
    void listarSalasDebeRetornar401SinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v2/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("No autorizado"));

        verifyNoInteractions(roomRepository);
    }

    @Test
    void listarSalasDebeRetornarArregloVacioCuandoNoHayResultados() throws Exception {
        when(roomRepository.findAllActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/v2/rooms")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(roomRepository).findAllActive();
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
        RoomService roomService(RoomRepository roomRepository) {
            return new RoomService(roomRepository);
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

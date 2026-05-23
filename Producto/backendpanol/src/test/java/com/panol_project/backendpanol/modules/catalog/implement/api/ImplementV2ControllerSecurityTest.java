package com.panol_project.backendpanol.modules.catalog.implement.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.panol_project.backendpanol.modules.catalog.category.application.contract.CategoryValidationContract;
import com.panol_project.backendpanol.modules.catalog.implement.application.ImplementDetailFacade;
import com.panol_project.backendpanol.modules.catalog.implement.application.ImplementService;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementRepository;
import com.panol_project.backendpanol.modules.catalog.location.application.contract.LocationValidationContract;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(ImplementV2Controller.class)
@Import({
        ImplementV2ControllerSecurityTest.TestSecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class ImplementV2ControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImplementRepository implementRepository;

    @MockBean
    private CategoryValidationContract categoryValidationContract;

    @MockBean
    private LocationValidationContract locationValidationContract;

    @MockBean
    private ImplementDetailFacade implementDetailFacade;

    @Test
    void editarImplementoDebeRetornar401SinAutenticacion() throws Exception {
        mockMvc.perform(put("/api/v2/implements/{implementUuid}", UUID.randomUUID())
                        .contentType("application/json")
                        .content(validUpdatePayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("No autorizado"));

        verifyNoInteractions(
                implementRepository,
                categoryValidationContract,
                locationValidationContract,
                implementDetailFacade
        );
    }

    @Test
    void editarImplementoDebeRetornar403ParaDocente() throws Exception {
        mockMvc.perform(put("/api/v2/implements/{implementUuid}", UUID.randomUUID())
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE")))
                        .contentType("application/json")
                        .content(validUpdatePayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado"));

        verifyNoInteractions(
                implementRepository,
                categoryValidationContract,
                locationValidationContract,
                implementDetailFacade
        );
    }

    private String validUpdatePayload() {
        return """
                {
                  "name":"Implemento QA",
                  "description":"Descripcion QA",
                  "categoryUuid":"11111111-1111-4111-8111-111111111111",
                  "locationUuid":"22222222-2222-4222-8222-222222222222",
                  "item_type":"fungible",
                  "min_stock":1,
                  "barcode":"QA-123",
                  "img_url":"https://example.com/qa.png",
                  "observations":"Observacion QA"
                }
                """;
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
        ImplementService implementService(
                ImplementRepository implementRepository,
                CategoryValidationContract categoryValidationContract,
                LocationValidationContract locationValidationContract
        ) {
            return new ImplementService(
                    implementRepository,
                    categoryValidationContract,
                    locationValidationContract
            );
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

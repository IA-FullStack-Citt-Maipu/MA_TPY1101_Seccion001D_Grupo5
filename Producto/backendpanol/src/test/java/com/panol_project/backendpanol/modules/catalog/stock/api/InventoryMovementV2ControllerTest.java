package com.panol_project.backendpanol.modules.catalog.stock.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.modules.catalog.stock.application.InventoryMovementService;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementDashboardSummary;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryItem;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryPage;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementTopImplementStat;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementTopUserStat;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.time.Instant;
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

@WebMvcTest(InventoryMovementV2Controller.class)
@Import({
        InventoryMovementV2ControllerTest.TestSecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class InventoryMovementV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryMovementService inventoryMovementService;

    @MockBean
    private CurrentUserUuidResolver currentUserUuidResolver;

    @Test
    void listarMovimientosDebeResolverNombreDesdeContrato() throws Exception {
        UUID implementUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        InventoryMovement movement = new InventoryMovement(
                implementUuid,
                MovementAction.STOCK_IN,
                2,
                userUuid,
                Instant.now(),
                "nota"
        );
        movement.setId("m1");
        movement.setPerformedByName("Carlos");

        when(inventoryMovementService.obtenerTodosMovimientos()).thenReturn(List.of(movement));

        mockMvc.perform(get("/api/v2/implements/movements")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "COORDINADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performed_by").value("Carlos"))
                .andExpect(jsonPath("$[0].action").value("stock_in"));
    }

    @Test
    void obtenerResumenMovimientosDebeRetornarPayloadAgregado() throws Exception {
        InventoryMovementDashboardSummary summary = new InventoryMovementDashboardSummary(
                18,
                List.of(new InventoryMovementTopUserStat("Coordinador QA Local", "COORDINADOR", 11)),
                List.of(new InventoryMovementTopImplementStat(UUID.randomUUID(), "Simulador", 9))
        );
        when(inventoryMovementService.obtenerResumenDashboard()).thenReturn(summary);

        mockMvc.perform(get("/api/v2/implements/movements/summary")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DIRECTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_movements").value(18))
                .andExpect(jsonPath("$.top_users[0].name").value("Coordinador QA Local"))
                .andExpect(jsonPath("$.top_users[0].role").value("COORDINADOR"))
                .andExpect(jsonPath("$.top_users[0].movement_count").value(11))
                .andExpect(jsonPath("$.top_implements[0].implement_name").value("Simulador"))
                .andExpect(jsonPath("$.top_implements[0].movement_count").value(9));
    }

    @Test
    void listarHistorialDebeRetornarPaginaEnriquecidaParaDirector() throws Exception {
        UUID implementUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        InventoryMovementHistoryItem item = new InventoryMovementHistoryItem(
                "44",
                implementUuid,
                "Simulador",
                "SIM-123",
                "reusable",
                "Simulacion",
                "Bodega 1",
                userUuid,
                "Ana Perez",
                "COORDINADOR",
                MovementAction.LOAN_DELIVERY,
                3,
                Instant.parse("2026-06-29T14:30:00Z"),
                "Entrega para laboratorio"
        );
        when(inventoryMovementService.obtenerHistorial(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new InventoryMovementHistoryPage(List.of(item), 1, 15, 27, 2));

        mockMvc.perform(get("/api/v2/implements/movements/history")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DIRECTOR")))
                        .param("page", "1")
                        .param("size", "15")
                        .param("search", "simulador")
                        .param("action", "loan_delivery")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("44"))
                .andExpect(jsonPath("$.items[0].implement_name").value("Simulador"))
                .andExpect(jsonPath("$.items[0].performed_by").value("Ana Perez"))
                .andExpect(jsonPath("$.items[0].performed_by_role").value("COORDINADOR"))
                .andExpect(jsonPath("$.items[0].action").value("loan_delivery"))
                .andExpect(jsonPath("$.total_items").value(27))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.has_next").value(true))
                .andExpect(jsonPath("$.has_previous").value(false));
    }

    @Test
    void listarHistorialDebeRetornar400CuandoElRangoEsInvalido() throws Exception {
        mockMvc.perform(get("/api/v2/implements/movements/history")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DIRECTOR")))
                        .param("from", "2026-07-01")
                        .param("to", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOVEMENT_HISTORY_RANGE_INVALID"));
    }

    @Test
    void listarHistorialDebeRetornar403ParaDocente() throws Exception {
        mockMvc.perform(get("/api/v2/implements/movements/history")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado"));

        verifyNoInteractions(inventoryMovementService);
    }

    @Test
    void registrarMovimientoDebeRetornar403ParaDirector() throws Exception {
        mockMvc.perform(post("/api/v2/implements/{implementUuid}/movements", UUID.randomUUID())
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DIRECTOR")))
                        .contentType("application/json")
                        .content(validRegisterPayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado"));

        verifyNoInteractions(currentUserUuidResolver);
    }

    @Test
    void registrarMovimientoDebeRetornar401SinAutenticacion() throws Exception {
        mockMvc.perform(post("/api/v2/implements/{implementUuid}/movements", UUID.randomUUID())
                        .contentType("application/json")
                        .content(validRegisterPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("No autorizado"));
    }

    private String validRegisterPayload() throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "movement_type", "stock_in",
                "quantity", 2,
                "notes", "Ingreso QA"
        ));
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

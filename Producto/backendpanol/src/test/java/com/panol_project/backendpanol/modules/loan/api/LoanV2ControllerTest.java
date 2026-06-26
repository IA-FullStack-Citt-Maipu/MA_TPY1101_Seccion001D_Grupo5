package com.panol_project.backendpanol.modules.loan.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.modules.loan.application.GestionPrestamoUseCase;
import com.panol_project.backendpanol.modules.loan.application.SolicitarPrestamoUseCase;
import com.panol_project.backendpanol.modules.loan.api.dto.CreateLoanV2Request;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDetailItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanImplementAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItemAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatus;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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

@WebMvcTest(LoanV2Controller.class)
@Import({
        LoanV2ControllerTest.TestSecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class LoanV2ControllerTest {

    private static final String FUTURE_SCHEDULED_AT = "2099-06-12T10:30:00-04:00";
    private static final String FUTURE_EXPECTED_RETURN_AT = "2099-06-12T13:45:00-04:00";
    private static final String FUTURE_OFFSET_SCHEDULED_AT = "2099-06-24T19:00:00-04:00";
    private static final String FUTURE_OFFSET_EXPECTED_RETURN_AT = "2099-06-24T19:53:00-04:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoanRepositoryPort loanRepositoryPort;

    @BeforeEach
    void setUp() {
        Mockito.reset(loanRepositoryPort);
    }

    @Test
    void solicitarPrestamoDebeCrearSolicitudConResumenEnriquecidoYTomarRequesterDesdeAuth() throws Exception {
        UUID authenticatedUserUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID subjectUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        UUID ignoredRequesterUuid = UUID.randomUUID();
        UUID loanUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = OffsetDateTime.parse(FUTURE_SCHEDULED_AT);
        OffsetDateTime expectedReturnAt = OffsetDateTime.parse(FUTURE_EXPECTED_RETURN_AT);

        LoanAggregate createdLoan = new LoanAggregate(
                loanUuid,
                authenticatedUserUuid,
                roomUuid,
                subjectUuid,
                LoanStatus.PENDING,
                scheduledAt,
                null,
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                List.of(new LoanDetailItem(implementUuid, 2, 0, 0))
        );
        LoanSummaryView response = new LoanSummaryView(
                loanUuid,
                authenticatedUserUuid,
                LoanStatus.PENDING,
                scheduledAt,
                null,
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                null,
                new LoanSummaryView.RoomView(roomUuid, "Sala 301"),
                new LoanSummaryView.SubjectView(subjectUuid, "Anatomia"),
                List.of(new LoanSummaryView.ItemView(implementUuid, "Fonendoscopio", 2, 0, 0))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(authenticatedUserUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, expectedReturnAt, null))
                .thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Fonendoscopio", true, 5)));
        when(loanRepositoryPort.existsPendingLoanConflict(
                eq(authenticatedUserUuid),
                eq(scheduledAt),
                eq(expectedReturnAt),
                eq(List.of(implementUuid))
        )).thenReturn(false);
        when(loanRepositoryPort.createPendingLoan(any(LoanCreateCommand.class))).thenReturn(createdLoan);
        when(loanRepositoryPort.findVisibleLoanSummaryByUuid(loanUuid)).thenReturn(Optional.of(response));

        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(authenticatedUserUuid, "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requester_uuid": "%s",
                                  "room_uuid": "%s",
                                  "subject_uuid": "%s",
                                  "scheduled_at": "%s",
                                  "expected_return_at": "%s",
                                  "items": [
                                    {
                                      "implement_uuid": "%s",
                                      "requested_quantity": 2
                                    }
                                  ]
                                }
                                """.formatted(
                                        ignoredRequesterUuid,
                                        roomUuid,
                                        subjectUuid,
                                        FUTURE_SCHEDULED_AT,
                                        FUTURE_EXPECTED_RETURN_AT,
                                        implementUuid
                                )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(response.uuid().toString()))
                .andExpect(jsonPath("$.requester_uuid").value(authenticatedUserUuid.toString()))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.scheduled_at").value(FUTURE_SCHEDULED_AT))
                .andExpect(jsonPath("$.room.uuid").value(roomUuid.toString()))
                .andExpect(jsonPath("$.room.name").value("Sala 301"))
                .andExpect(jsonPath("$.subject.uuid").value(subjectUuid.toString()))
                .andExpect(jsonPath("$.subject.name").value("Anatomia"))
                .andExpect(jsonPath("$.items[0].implement_uuid").value(implementUuid.toString()))
                .andExpect(jsonPath("$.items[0].implement_name").value("Fonendoscopio"))
                .andExpect(jsonPath("$.items[0].requested_quantity").value(2))
                .andExpect(jsonPath("$.items[0].reserved_quantity").value(0))
                .andExpect(jsonPath("$.items[0].delivered_quantity").value(0));

        ArgumentCaptor<LoanCreateCommand> commandCaptor = ArgumentCaptor.forClass(LoanCreateCommand.class);
        verify(loanRepositoryPort).createPendingLoan(commandCaptor.capture());

        LoanCreateCommand command = commandCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(authenticatedUserUuid, command.requesterUuid());
        org.junit.jupiter.api.Assertions.assertEquals(roomUuid, command.roomUuid());
        org.junit.jupiter.api.Assertions.assertEquals(subjectUuid, command.subjectUuid());
        org.junit.jupiter.api.Assertions.assertEquals(expectedReturnAt.toInstant(), command.expectedReturnAt().toInstant());
        org.junit.jupiter.api.Assertions.assertEquals(1, command.requestedItems().size());
        org.junit.jupiter.api.Assertions.assertEquals(implementUuid, command.requestedItems().getFirst().implementUuid());
        org.junit.jupiter.api.Assertions.assertEquals(2, command.requestedItems().getFirst().requestedQuantity());
    }

    @Test
    void solicitarPrestamoDebePermitirRolCoordinador() throws Exception {
        UUID authenticatedUserUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        UUID loanUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = OffsetDateTime.parse(FUTURE_SCHEDULED_AT);

        LoanAggregate createdLoan = new LoanAggregate(
                loanUuid,
                authenticatedUserUuid,
                roomUuid,
                null,
                LoanStatus.PENDING,
                scheduledAt,
                null,
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                List.of(new LoanDetailItem(implementUuid, 1, 0, 0))
        );
        LoanSummaryView response = new LoanSummaryView(
                loanUuid,
                authenticatedUserUuid,
                LoanStatus.PENDING,
                scheduledAt,
                null,
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                null,
                new LoanSummaryView.RoomView(roomUuid, "Sala 302"),
                null,
                List.of(new LoanSummaryView.ItemView(implementUuid, "Guantes", 1, 0, 0))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(authenticatedUserUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, null, null))
                .thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Guantes", true, 5)));
        when(loanRepositoryPort.existsPendingLoanConflict(
                eq(authenticatedUserUuid),
                eq(scheduledAt),
                isNull(),
                eq(List.of(implementUuid))
        )).thenReturn(false);
        when(loanRepositoryPort.createPendingLoan(any(LoanCreateCommand.class))).thenReturn(createdLoan);
        when(loanRepositoryPort.findVisibleLoanSummaryByUuid(loanUuid)).thenReturn(Optional.of(response));

        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(authenticatedUserUuid, "COORDINADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "room_uuid": "%s",
                                  "scheduled_at": "%s",
                                  "items": [
                                    {
                                      "implement_uuid": "%s",
                                      "requested_quantity": 1
                                    }
                                  ]
                                }
                                """.formatted(roomUuid, FUTURE_SCHEDULED_AT, implementUuid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(response.uuid().toString()))
                .andExpect(jsonPath("$.requester_uuid").value(authenticatedUserUuid.toString()))
                .andExpect(jsonPath("$.room.name").value("Sala 302"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR"})
    void solicitarPrestamoDebeRetornar403ParaRolesNoDocente(String role) throws Exception {
                mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado"));

        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarPrestamoDebeRetornar401SinAutenticacion() throws Exception {
        mockMvc.perform(post("/api/v2/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"))
                .andExpect(jsonPath("$.message").value("No autorizado"));

        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarPrestamoDebeRetornar400CuandoFaltaRoomUuid() throws Exception {
        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduled_at": "%s",
                                  "items": [
                                    {
                                      "implement_uuid": "%s",
                                      "requested_quantity": 1
                                    }
                                  ]
                                }
                                """.formatted(FUTURE_SCHEDULED_AT, UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void objectMapperDebePreservarOffsetEnFechasDePrestamoAunqueElContextoSeaUtc() throws Exception {
        ObjectMapper utcMapper = objectMapper.copy();
        utcMapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        CreateLoanV2Request request = utcMapper.readValue(
                """
                        {
                          "room_uuid": "%s",
                          "scheduled_at": "%s",
                          "expected_return_at": "%s",
                          "items": [
                            {
                              "implement_uuid": "%s",
                              "requested_quantity": 1
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID(), FUTURE_OFFSET_SCHEDULED_AT, FUTURE_OFFSET_EXPECTED_RETURN_AT, UUID.randomUUID()),
                CreateLoanV2Request.class
        );

        org.junit.jupiter.api.Assertions.assertEquals(ZoneOffset.ofHours(-4), request.scheduledAt().getOffset());
        org.junit.jupiter.api.Assertions.assertEquals(19, request.scheduledAt().getHour());
        org.junit.jupiter.api.Assertions.assertEquals(ZoneOffset.ofHours(-4), request.expectedReturnAt().getOffset());
        org.junit.jupiter.api.Assertions.assertEquals(19, request.expectedReturnAt().getHour());
        org.junit.jupiter.api.Assertions.assertEquals(53, request.expectedReturnAt().getMinute());
    }

    @Test
    void solicitarPrestamoDebeRetornar409CuandoExisteSolicitudPendienteSolapada() throws Exception {
        UUID authenticatedUserUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = OffsetDateTime.parse(FUTURE_SCHEDULED_AT);

        when(loanRepositoryPort.existsActiveRequesterByUuid(authenticatedUserUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(
                eq(List.of(implementUuid)),
                any(OffsetDateTime.class),
                isNull(),
                isNull()
        )).thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Implemento prueba", true, 5)));
        when(loanRepositoryPort.existsPendingLoanConflict(
                eq(authenticatedUserUuid),
                any(OffsetDateTime.class),
                isNull(),
                eq(List.of(implementUuid))
        ))
                .thenReturn(true);

        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(authenticatedUserUuid, "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "room_uuid": "%s",
                                  "scheduled_at": "%s",
                                  "items": [
                                    {
                                      "implement_uuid": "%s",
                                      "requested_quantity": 1
                                    }
                                  ]
                                }
                                """.formatted(roomUuid, FUTURE_SCHEDULED_AT, implementUuid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_DUPLICATE_REQUEST"))
                .andExpect(jsonPath("$.message").value("Ya tienes una solicitud pendiente con uno o m\u00e1s de estos implementos"));
    }

    @Test
    void solicitarPrestamoDebeRetornar400CuandoFaltaScheduledAt() throws Exception {
        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "room_uuid": "%s",
                                  "items": [
                                    {
                                      "implement_uuid": "%s",
                                      "requested_quantity": 1
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarPrestamoDebeRetornar400CuandoItemsEstaVacio() throws Exception {
        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "room_uuid": "%s",
                                  "scheduled_at": "%s",
                                  "items": []
                                }
                                """.formatted(UUID.randomUUID(), FUTURE_SCHEDULED_AT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarPrestamoDebeRetornar400CuandoFaltaImplementUuid() throws Exception {
        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "room_uuid": "%s",
                                  "scheduled_at": "%s",
                                  "items": [
                                    {
                                      "requested_quantity": 1
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), FUTURE_SCHEDULED_AT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarPrestamoDebeRetornar400CuandoRequestedQuantityNoEsPositiva() throws Exception {
        mockMvc.perform(post("/api/v2/loans")
                        .with(authentication(jwtAuthentication(UUID.randomUUID(), "DOCENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "room_uuid": "%s",
                                  "scheduled_at": "%s",
                                  "items": [
                                    {
                                      "implement_uuid": "%s",
                                      "requested_quantity": 0
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), FUTURE_SCHEDULED_AT, UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(loanRepositoryPort);
    }

    private String validCreatePayload() throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "room_uuid", UUID.randomUUID(),
                "scheduled_at", FUTURE_SCHEDULED_AT,
                "items", List.of(java.util.Map.of(
                        "implement_uuid", UUID.randomUUID(),
                        "requested_quantity", 1
                ))
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
        SolicitarPrestamoUseCase solicitarPrestamoUseCase(LoanRepositoryPort loanRepositoryPort) {
            return new SolicitarPrestamoUseCase(
                    loanRepositoryPort,
                    Clock.fixed(Instant.parse("2099-06-01T14:00:00Z"), ZoneOffset.ofHours(-4))
            );
        }

        @Bean
        GestionPrestamoUseCase gestionPrestamoUseCase(LoanRepositoryPort loanRepositoryPort) {
            return new GestionPrestamoUseCase(loanRepositoryPort);
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

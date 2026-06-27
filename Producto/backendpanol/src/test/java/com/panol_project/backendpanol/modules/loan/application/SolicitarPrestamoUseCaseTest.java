package com.panol_project.backendpanol.modules.loan.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDetailItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanImplementAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItemAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatus;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.ConflictException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolicitarPrestamoUseCaseTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2099-06-01T14:00:00Z"), ZoneOffset.ofHours(-4));
    private static final OffsetDateTime FUTURE_SCHEDULED_AT = OffsetDateTime.parse("2099-06-12T10:30:00-04:00");
    private static final OffsetDateTime FUTURE_EXPECTED_RETURN_BASE = OffsetDateTime.parse("2099-06-12T10:00:00-04:00");
    private static final OffsetDateTime FUTURE_SUNDAY_SCHEDULED_AT = OffsetDateTime.parse("2099-06-14T10:30:00-04:00");
    private static final OffsetDateTime FUTURE_OUT_OF_RANGE_SCHEDULED_AT = OffsetDateTime.parse("2099-06-12T07:30:00-04:00");
    private static final OffsetDateTime FUTURE_INVALID_UPDATE_SCHEDULED_AT = OffsetDateTime.parse("2099-06-19T21:30:00-04:00");
    private static final OffsetDateTime FUTURE_INVALID_UPDATE_RETURN_AT = OffsetDateTime.parse("2099-06-19T23:30:00-04:00");

    @Mock
    private LoanRepositoryPort loanRepositoryPort;

    @Test
    void solicitarDebeRechazarFechaProgramadaEnElPasado() {
        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                OffsetDateTime.now().minusMinutes(5),
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_PAST_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarFechaDevolucionNoPosteriorALaProgramada() {
        OffsetDateTime scheduledAt = FUTURE_EXPECTED_RETURN_BASE;
        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                scheduledAt.minusMinutes(15),
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_EXPECTED_RETURN_INVALID", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeCrearPrestamoAutoReservadoYRetornarResumenEnriquecido() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID subjectUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        UUID loanUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = FUTURE_SCHEDULED_AT;

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                subjectUuid,
                scheduledAt,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 2))
        );

        LoanAggregate createdLoan = new LoanAggregate(
                loanUuid,
                requesterUuid,
                roomUuid,
                subjectUuid,
                LoanStatus.PENDING,
                scheduledAt,
                null,
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                List.of(new LoanDetailItem(implementUuid, 2, 0, 0))
        );
        LoanSummaryView expectedSummary = new LoanSummaryView(
                loanUuid,
                requesterUuid,
                LoanStatus.APPROVED,
                scheduledAt,
                null,
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                null,
                new LoanSummaryView.RoomView(roomUuid, "Sala 301"),
                new LoanSummaryView.SubjectView(subjectUuid, "Anatomia"),
                List.of(new LoanSummaryView.ItemView(implementUuid, "Fonendoscopio", 2, 2, 0))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, null, null))
                .thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Fonendoscopio", true, 5)));
        when(loanRepositoryPort.existsPendingLoanConflict(requesterUuid, scheduledAt, null, List.of(implementUuid)))
                .thenReturn(false);
        when(loanRepositoryPort.createPendingLoan(any(LoanCreateCommand.class))).thenReturn(createdLoan);
        when(loanRepositoryPort.findVisibleLoanSummaryByUuid(loanUuid)).thenReturn(Optional.of(expectedSummary));

        SolicitarPrestamoUseCase useCase = useCase();

        LoanSummaryView result = useCase.solicitar(command);

        assertSame(expectedSummary, result);

        ArgumentCaptor<LoanCreateCommand> createCommandCaptor = ArgumentCaptor.forClass(LoanCreateCommand.class);
        verify(loanRepositoryPort).createPendingLoan(createCommandCaptor.capture());

        LoanCreateCommand persisted = createCommandCaptor.getValue();
        assertEquals(requesterUuid, persisted.requesterUuid());
        assertEquals(roomUuid, persisted.roomUuid());
        assertEquals(subjectUuid, persisted.subjectUuid());
        assertEquals(scheduledAt, persisted.scheduledAt());
        assertEquals(1, persisted.requestedItems().size());
        assertEquals(implementUuid, persisted.requestedItems().getFirst().implementUuid());
        assertEquals(2, persisted.requestedItems().getFirst().requestedQuantity());

        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).existsActiveSubjectByUuid(subjectUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort).findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, null, null);
        verify(loanRepositoryPort).existsPendingLoanConflict(requesterUuid, scheduledAt, null, List.of(implementUuid));
        verify(loanRepositoryPort).reviewLoan(any());
        verify(loanRepositoryPort).findVisibleLoanSummaryByUuid(loanUuid);
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarImplementosDuplicadosAntesDePersistir() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                FUTURE_SCHEDULED_AT,
                null,
                null,
                List.of(
                        new SolicitarPrestamoItemCommand(implementUuid, 1),
                        new SolicitarPrestamoItemCommand(implementUuid, 2)
                )
        );

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_ITEM_DUPLICATE", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRetornarRoomNotFoundAntesDeValidarLoDemas() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                FUTURE_SCHEDULED_AT,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(false);

        SolicitarPrestamoUseCase useCase = useCase();

        NotFoundException ex = assertThrows(NotFoundException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_ROOM_NOT_FOUND", ex.getCode());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRetornarSubjectNotFoundAntesDeConsultarImplementos() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID subjectUuid = UUID.randomUUID();

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                subjectUuid,
                FUTURE_SCHEDULED_AT,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)).thenReturn(false);

        SolicitarPrestamoUseCase useCase = useCase();

        NotFoundException ex = assertThrows(NotFoundException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SUBJECT_NOT_FOUND", ex.getCode());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).existsActiveSubjectByUuid(subjectUuid);
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRetornarImplementNotFoundSinEvaluarDuplicidadDeSolicitud() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                FUTURE_SCHEDULED_AT,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid)).thenReturn(Optional.empty());

        SolicitarPrestamoUseCase useCase = useCase();

        NotFoundException ex = assertThrows(NotFoundException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_IMPLEMENT_NOT_FOUND", ex.getCode());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort, never()).existsPendingLoanConflict(any(), any(), any(), any());
        verify(loanRepositoryPort, never()).createPendingLoan(any());
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRetornarImplementInactiveSinEvaluarDuplicidadDeSolicitud() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                FUTURE_SCHEDULED_AT,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, false)));

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_IMPLEMENT_INACTIVE", ex.getCode());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort, never()).existsPendingLoanConflict(any(), any(), any(), any());
        verify(loanRepositoryPort, never()).createPendingLoan(any());
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRetornar409CuandoExisteSolicitudPendienteConInterseccionDeImplementosEnHorarioSolapado() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = FUTURE_SCHEDULED_AT;

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                scheduledAt,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(
                List.of(implementUuid),
                scheduledAt,
                null,
                null
        )).thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Implemento prueba", true, 5)));
        when(loanRepositoryPort.existsPendingLoanConflict(requesterUuid, scheduledAt, null, List.of(implementUuid)))
                .thenReturn(true);

        SolicitarPrestamoUseCase useCase = useCase();

        ApiException ex = assertThrows(ApiException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_DUPLICATE_REQUEST", ex.getCode());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Ya tienes una solicitud activa con uno o mas de estos implementos en la misma ventana horaria", ex.getMessage());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort).findRequestedItemAvailabilities(
                List.of(implementUuid),
                scheduledAt,
                null,
                null
        );
        verify(loanRepositoryPort).existsPendingLoanConflict(requesterUuid, scheduledAt, null, List.of(implementUuid));
        verify(loanRepositoryPort, never()).createPendingLoan(any());
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarDomingos() {
        OffsetDateTime scheduledAt = FUTURE_SUNDAY_SCHEDULED_AT;

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_DAY_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarHorasFueraDeRango() {
        OffsetDateTime scheduledAt = FUTURE_OUT_OF_RANGE_SCHEDULED_AT;

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_TIME_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarFechasProgramadasMasAllaDeDosSemanas() {
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2099-06-16T10:30:00-04:00");

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_RANGE_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarCuandoLaCantidadExcedeLaDisponibilidad() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = FUTURE_SCHEDULED_AT;

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                scheduledAt,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 7))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, null, null))
                .thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Arcillas de dientes", true, 5)));

        SolicitarPrestamoUseCase useCase = useCase();

        ConflictException ex = assertThrows(ConflictException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_STOCK_CONFLICT", ex.getCode());
        assertEquals(
                "Solo puedes solicitar dentro del stock disponible. Arcillas de dientes tiene 5 unidad(es) disponibles para la fecha y hora seleccionadas.",
                ex.getMessage()
        );
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort).findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, null, null);
        verify(loanRepositoryPort, never()).existsPendingLoanConflict(any(), any(), any(), any());
        verify(loanRepositoryPort, never()).createPendingLoan(any());
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarScheduledAtPasado() {
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2020-01-01T08:00:00-04:00");

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                null,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_PAST_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void modificarDebeRechazarPrestamoNoAprobadoAntesDeValidarHorario() {
        UUID loanUuid = UUID.randomUUID();
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                FUTURE_INVALID_UPDATE_SCHEDULED_AT,
                FUTURE_INVALID_UPDATE_RETURN_AT,
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
        );

        LoanSummaryView existingLoan = new LoanSummaryView(
                loanUuid,
                requesterUuid,
                LoanStatus.PREPARED,
                OffsetDateTime.parse("2026-06-19T12:00:00-04:00"),
                OffsetDateTime.parse("2026-06-19T14:00:00-04:00"),
                OffsetDateTime.parse("2026-06-09T10:00:00-04:00"),
                null,
                new LoanSummaryView.RoomView(roomUuid, "Sala 321"),
                null,
                List.of(new LoanSummaryView.ItemView(implementUuid, "Fonendoscopio", 1, 1, 0))
        );

        when(loanRepositoryPort.findVisibleLoanSummaryByUuid(loanUuid)).thenReturn(Optional.of(existingLoan));

        SolicitarPrestamoUseCase useCase = useCase();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.modificar(loanUuid, command));

        assertEquals("LOAN_UPDATE_INVALID_STATE", ex.getCode());
        verify(loanRepositoryPort).findVisibleLoanSummaryByUuid(loanUuid);
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    private SolicitarPrestamoUseCase useCase() {
        return new SolicitarPrestamoUseCase(loanRepositoryPort, FIXED_CLOCK);
    }
}

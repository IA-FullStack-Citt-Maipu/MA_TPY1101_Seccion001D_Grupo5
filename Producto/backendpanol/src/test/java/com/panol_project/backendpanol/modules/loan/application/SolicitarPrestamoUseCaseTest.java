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
import java.time.OffsetDateTime;
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
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_PAST_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarFechaDevolucionNoPosteriorALaProgramada() {
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-06-12T10:00:00-04:00");
        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                scheduledAt.minusMinutes(15),
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_EXPECTED_RETURN_INVALID", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeCrearPrestamoPendienteYRetornarResumenEnriquecido() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID subjectUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        UUID loanUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-06-12T10:30:00-04:00");

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                subjectUuid,
                scheduledAt,
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
                LoanStatus.PENDING,
                scheduledAt,
                null,
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                new LoanSummaryView.RoomView(roomUuid, "Sala 301"),
                new LoanSummaryView.SubjectView(subjectUuid, "Anatomia"),
                List.of(new LoanSummaryView.ItemView(implementUuid, "Fonendoscopio", 2, 0, 0))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, null, null))
                .thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Fonendoscopio", true, 5)));
        when(loanRepositoryPort.existsPendingLoanConflict(requesterUuid, List.of(implementUuid))).thenReturn(false);
        when(loanRepositoryPort.createPendingLoan(any(LoanCreateCommand.class))).thenReturn(createdLoan);
        when(loanRepositoryPort.findVisibleLoanSummaryByUuid(loanUuid)).thenReturn(Optional.of(expectedSummary));

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

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
        verify(loanRepositoryPort).existsPendingLoanConflict(requesterUuid, List.of(implementUuid));
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
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                List.of(
                        new SolicitarPrestamoItemCommand(implementUuid, 1),
                        new SolicitarPrestamoItemCommand(implementUuid, 2)
                )
        );

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

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
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(false);

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

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
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)).thenReturn(false);

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

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
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid)).thenReturn(Optional.empty());

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_IMPLEMENT_NOT_FOUND", ex.getCode());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort, never()).existsPendingLoanConflict(any(), any());
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
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, false)));

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_IMPLEMENT_INACTIVE", ex.getCode());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort, never()).existsPendingLoanConflict(any(), any());
        verify(loanRepositoryPort, never()).createPendingLoan(any());
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRetornar409CuandoExisteSolicitudPendienteConInterseccionDeImplementos() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(
                List.of(implementUuid),
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                null
        )).thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Implemento prueba", true, 5)));
        when(loanRepositoryPort.existsPendingLoanConflict(requesterUuid, List.of(implementUuid))).thenReturn(true);

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        ApiException ex = assertThrows(ApiException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_DUPLICATE_REQUEST", ex.getCode());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Ya tienes una solicitud pendiente con uno o m\u00e1s de estos implementos", ex.getMessage());
        verify(loanRepositoryPort).existsActiveRequesterByUuid(requesterUuid);
        verify(loanRepositoryPort).existsActiveRoomByUuid(roomUuid);
        verify(loanRepositoryPort).findImplementAvailabilityByUuid(implementUuid);
        verify(loanRepositoryPort).findRequestedItemAvailabilities(
                List.of(implementUuid),
                OffsetDateTime.parse("2026-06-12T10:30:00-04:00"),
                null,
                null
        );
        verify(loanRepositoryPort).existsPendingLoanConflict(requesterUuid, List.of(implementUuid));
        verify(loanRepositoryPort, never()).createPendingLoan(any());
        verifyNoMoreInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarDomingos() {
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-06-14T10:30:00-04:00");

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_DAY_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarHorasFueraDeRango() {
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-06-12T07:30:00-04:00");

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                scheduledAt,
                null,
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_TIME_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }

    @Test
    void solicitarDebeRechazarCuandoLaCantidadExcedeLaDisponibilidad() {
        UUID requesterUuid = UUID.randomUUID();
        UUID roomUuid = UUID.randomUUID();
        UUID implementUuid = UUID.randomUUID();
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-06-12T10:30:00-04:00");

        SolicitarPrestamoCommand command = new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                null,
                scheduledAt,
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 7))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.findImplementAvailabilityByUuid(implementUuid))
                .thenReturn(Optional.of(new LoanImplementAvailability(implementUuid, true)));
        when(loanRepositoryPort.findRequestedItemAvailabilities(List.of(implementUuid), scheduledAt, null, null))
                .thenReturn(List.of(new LoanRequestedItemAvailability(implementUuid, "Arcillas de dientes", true, 5)));

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

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
        verify(loanRepositoryPort, never()).existsPendingLoanConflict(any(), any());
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
                List.of(new SolicitarPrestamoItemCommand(UUID.randomUUID(), 1))
        );

        SolicitarPrestamoUseCase useCase = new SolicitarPrestamoUseCase(loanRepositoryPort);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> useCase.solicitar(command));

        assertEquals("LOAN_SCHEDULE_PAST_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(loanRepositoryPort);
    }
}

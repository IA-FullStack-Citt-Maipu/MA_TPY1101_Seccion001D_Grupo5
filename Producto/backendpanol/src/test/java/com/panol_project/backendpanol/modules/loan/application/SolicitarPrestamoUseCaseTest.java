package com.panol_project.backendpanol.modules.loan.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDetailItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatus;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.shared.error.BadRequestException;
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
                OffsetDateTime.parse("2026-05-21T21:00:00-04:00"),
                new LoanSummaryView.RoomView(roomUuid, "Sala 301"),
                new LoanSummaryView.SubjectView(subjectUuid, "Anatomia"),
                List.of(new LoanSummaryView.ItemView(implementUuid, "Fonendoscopio", 2, 0, 0))
        );

        when(loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveRoomByUuid(roomUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)).thenReturn(true);
        when(loanRepositoryPort.existsActiveImplementByUuid(implementUuid)).thenReturn(true);
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
        verify(loanRepositoryPort).existsActiveImplementByUuid(implementUuid);
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
        verify(loanRepositoryPort, never()).createPendingLoan(any());
        verifyNoMoreInteractions(loanRepositoryPort);
    }
}

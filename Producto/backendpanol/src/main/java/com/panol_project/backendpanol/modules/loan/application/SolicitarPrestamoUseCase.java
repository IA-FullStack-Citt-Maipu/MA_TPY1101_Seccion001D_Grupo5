package com.panol_project.backendpanol.modules.loan.application;

import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanImplementAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.modules.loan.domain.LoanUpdateCommand;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitarPrestamoUseCase {

    private final LoanRepositoryPort loanRepositoryPort;

    public SolicitarPrestamoUseCase(LoanRepositoryPort loanRepositoryPort) {
        this.loanRepositoryPort = loanRepositoryPort;
    }

    @Transactional
    public LoanSummaryView solicitar(SolicitarPrestamoCommand command) {
        validateCommand(command);

        UUID requesterUuid = command.requesterUuid();
        UUID roomUuid = command.roomUuid();
        UUID subjectUuid = command.subjectUuid();

        if (!loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)) {
            throw new NotFoundException("LOAN_REQUESTER_NOT_FOUND", "El solicitante no existe o esta inactivo");
        }
        if (roomUuid != null && !loanRepositoryPort.existsActiveRoomByUuid(roomUuid)) {
            throw new NotFoundException("LOAN_ROOM_NOT_FOUND", "La sala seleccionada no existe o esta inactiva");
        }
        if (subjectUuid != null && !loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)) {
            throw new NotFoundException("LOAN_SUBJECT_NOT_FOUND", "La asignatura seleccionada no existe o esta inactiva");
        }

        List<LoanRequestedItem> requestedItems = command.requestedItems().stream()
                .map(item -> new LoanRequestedItem(item.implementUuid(), item.requestedQuantity()))
                .toList();

        for (LoanRequestedItem item : requestedItems) {
            LoanImplementAvailability implement = loanRepositoryPort.findImplementAvailabilityByUuid(item.implementUuid())
                    .orElseThrow(() -> new NotFoundException("LOAN_IMPLEMENT_NOT_FOUND", "Implemento no encontrado"));
            if (!implement.active()) {
                throw new BadRequestException("LOAN_IMPLEMENT_INACTIVE", "El implemento seleccionado est\u00e1 inactivo");
            }
        }

        List<UUID> implementUuids = requestedItems.stream()
                .map(LoanRequestedItem::implementUuid)
                .toList();

        if (loanRepositoryPort.existsPendingLoanConflict(requesterUuid, implementUuids)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LOAN_DUPLICATE_REQUEST",
                    "Ya tienes una solicitud pendiente con uno o m\u00e1s de estos implementos"
            );
        }

        LoanAggregate loan = loanRepositoryPort.createPendingLoan(
                new LoanCreateCommand(
                        requesterUuid,
                        roomUuid,
                        subjectUuid,
                        command.scheduledAt(),
                        command.expectedReturnAt(),
                        requestedItems
                )
        );

        return loanRepositoryPort.findVisibleLoanSummaryByUuid(loan.uuid())
                .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado"));
    }

    @Transactional
    public LoanSummaryView modificar(UUID loanUuid, SolicitarPrestamoCommand command) {
        if (loanUuid == null) {
            throw new BadRequestException("LOAN_UUID_REQUIRED", "loan_uuid es obligatorio");
        }
        validateCommand(command);

        UUID requesterUuid = command.requesterUuid();
        UUID roomUuid = command.roomUuid();
        UUID subjectUuid = command.subjectUuid();

        if (!loanRepositoryPort.existsActiveRequesterByUuid(requesterUuid)) {
            throw new NotFoundException("LOAN_REQUESTER_NOT_FOUND", "El solicitante no existe o esta inactivo");
        }
        if (roomUuid != null && !loanRepositoryPort.existsActiveRoomByUuid(roomUuid)) {
            throw new NotFoundException("LOAN_ROOM_NOT_FOUND", "La sala seleccionada no existe o esta inactiva");
        }
        if (subjectUuid != null && !loanRepositoryPort.existsActiveSubjectByUuid(subjectUuid)) {
            throw new NotFoundException("LOAN_SUBJECT_NOT_FOUND", "La asignatura seleccionada no existe o esta inactiva");
        }

        List<LoanRequestedItem> requestedItems = command.requestedItems().stream()
                .map(item -> new LoanRequestedItem(item.implementUuid(), item.requestedQuantity()))
                .toList();

        for (LoanRequestedItem item : requestedItems) {
            LoanImplementAvailability implement = loanRepositoryPort.findImplementAvailabilityByUuid(item.implementUuid())
                    .orElseThrow(() -> new NotFoundException("LOAN_IMPLEMENT_NOT_FOUND", "Implemento no encontrado"));
            if (!implement.active()) {
                throw new BadRequestException("LOAN_IMPLEMENT_INACTIVE", "El implemento seleccionado está inactivo");
            }
        }

        List<UUID> implementUuids = requestedItems.stream()
                .map(LoanRequestedItem::implementUuid)
                .toList();

        if (loanRepositoryPort.existsPendingLoanConflict(requesterUuid, loanUuid, implementUuids)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LOAN_DUPLICATE_REQUEST",
                    "Ya tienes una solicitud pendiente con uno o más de estos implementos"
            );
        }

        LoanAggregate loan = loanRepositoryPort.updatePendingLoan(
                new LoanUpdateCommand(
                        loanUuid,
                        requesterUuid,
                        roomUuid,
                        subjectUuid,
                        command.scheduledAt(),
                        command.expectedReturnAt(),
                        requestedItems
                )
        );

        return loanRepositoryPort.findVisibleLoanSummaryByUuid(loan.uuid())
                .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado"));
    }

    private void validateCommand(SolicitarPrestamoCommand command) {
        if (command == null) {
            throw new BadRequestException("LOAN_REQUEST_INVALID", "La solicitud del prestamo es obligatoria");
        }
        if (command.requesterUuid() == null) {
            throw new BadRequestException("LOAN_REQUESTER_REQUIRED", "El solicitante autenticado es obligatorio");
        }
        if (command.roomUuid() == null) {
            throw new BadRequestException("LOAN_ROOM_REQUIRED", "room_uuid es obligatorio");
        }
        validateScheduledAt(command.scheduledAt());
        validateExpectedReturnAt(command.scheduledAt(), command.expectedReturnAt());

        List<SolicitarPrestamoItemCommand> items = command.requestedItems();
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("LOAN_ITEMS_REQUIRED", "Debes incluir al menos un implemento solicitado");
        }

        Set<UUID> uniqueImplementUuids = new HashSet<>();
        for (SolicitarPrestamoItemCommand item : items) {
            if (item == null || item.implementUuid() == null) {
                throw new BadRequestException("LOAN_ITEM_IMPLEMENT_REQUIRED", "Cada item debe incluir implement_uuid");
            }
            if (item.requestedQuantity() == null || item.requestedQuantity() <= 0) {
                throw new BadRequestException("LOAN_ITEM_QUANTITY_INVALID", "requested_quantity debe ser mayor a cero");
            }
            if (!uniqueImplementUuids.add(item.implementUuid())) {
                throw new BadRequestException("LOAN_ITEM_DUPLICATE", "No puedes incluir implementos duplicados en la solicitud");
            }
        }
    }

    private void validateScheduledAt(OffsetDateTime scheduledAt) {
        if (scheduledAt == null) {
            throw new BadRequestException("LOAN_SCHEDULE_REQUIRED", "scheduled_at es obligatorio");
        }
        if (scheduledAt.isBefore(OffsetDateTime.now())) {
            throw new BadRequestException(
                    "LOAN_SCHEDULE_PAST_NOT_ALLOWED",
                    "scheduled_at no puede estar en una fecha u hora pasada"
            );
        }
    }

    private void validateExpectedReturnAt(OffsetDateTime scheduledAt, OffsetDateTime expectedReturnAt) {
        if (expectedReturnAt == null) {
            return;
        }
        if (expectedReturnAt.isBefore(OffsetDateTime.now())) {
            throw new BadRequestException(
                    "LOAN_EXPECTED_RETURN_PAST_NOT_ALLOWED",
                    "expected_return_at no puede estar en una fecha u hora pasada"
            );
        }
        if (!expectedReturnAt.isAfter(scheduledAt)) {
            throw new BadRequestException(
                    "LOAN_EXPECTED_RETURN_INVALID",
                    "expected_return_at debe ser posterior a scheduled_at"
            );
        }
    }
}

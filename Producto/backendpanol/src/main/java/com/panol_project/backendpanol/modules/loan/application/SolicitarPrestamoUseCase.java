package com.panol_project.backendpanol.modules.loan.application;

import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItem;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitarPrestamoUseCase {

    private final LoanRepositoryPort loanRepositoryPort;
    private final OutboxService outboxService;

    public SolicitarPrestamoUseCase(LoanRepositoryPort loanRepositoryPort, OutboxService outboxService) {
        this.loanRepositoryPort = loanRepositoryPort;
        this.outboxService = outboxService;
    }

    @Transactional
    public LoanAggregate solicitar(SolicitarPrestamoCommand command) {
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
            if (!loanRepositoryPort.existsActiveImplementByUuid(item.implementUuid())) {
                throw new NotFoundException("LOAN_IMPLEMENT_NOT_FOUND", "Uno o mas implementos no existen o estan inactivos");
            }
        }

        LoanAggregate loan = loanRepositoryPort.createPendingLoan(
                new LoanCreateCommand(
                        requesterUuid,
                        roomUuid,
                        subjectUuid,
                        command.scheduledAt(),
                        command.dueDate(),
                        requestedItems
                )
        );

        outboxService.enqueue(
                "loan",
                loan.uuid(),
                "LoanRequested",
                requesterUuid,
                java.util.Map.of(
                        "requester_uuid", requesterUuid.toString(),
                        "room_uuid", roomUuid == null ? "" : roomUuid.toString(),
                        "subject_uuid", subjectUuid == null ? "" : subjectUuid.toString(),
                        "scheduled_at", command.scheduledAt().toString(),
                        "due_date", command.dueDate() == null ? "" : command.dueDate().toString(),
                        "items", requestedItems.stream().map(item -> java.util.Map.of(
                                "implement_uuid", item.implementUuid().toString(),
                                "requested_quantity", item.requestedQuantity()
                        )).toList()
                )
        );

        return loan;
    }

    private void validateCommand(SolicitarPrestamoCommand command) {
        if (command == null) {
            throw new BadRequestException("LOAN_REQUEST_INVALID", "La solicitud del prestamo es obligatoria");
        }
        if (command.requesterUuid() == null) {
            throw new BadRequestException("LOAN_REQUESTER_REQUIRED", "El solicitante autenticado es obligatorio");
        }
        if (command.scheduledAt() == null) {
            throw new BadRequestException("LOAN_SCHEDULE_REQUIRED", "scheduled_at es obligatorio");
        }
        if (command.dueDate() != null && command.dueDate().isBefore(command.scheduledAt())) {
            throw new BadRequestException("LOAN_DUE_DATE_INVALID", "due_date no puede ser anterior a scheduled_at");
        }

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
                throw new BadRequestException("LOAN_ITEM_DUPLICATE", "No puedes repetir implementos en la misma solicitud");
            }
        }
    }
}

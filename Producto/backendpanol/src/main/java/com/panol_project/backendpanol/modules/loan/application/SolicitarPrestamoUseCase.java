package com.panol_project.backendpanol.modules.loan.application;

import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanImplementAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItemAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatus;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.modules.loan.domain.LoanUpdateCommand;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.ConflictException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolicitarPrestamoUseCase {

    private static final int MAX_NOTES_LENGTH = 1000;
    private static final int MAX_SCHEDULE_DAYS_AHEAD = 14;
    private static final UUID DEFAULT_SYSTEM_USER_UUID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private final LoanRepositoryPort loanRepositoryPort;
    private final Clock clock;
    private final UUID systemUserUuid;

    @Autowired
    public SolicitarPrestamoUseCase(
            LoanRepositoryPort loanRepositoryPort,
            @Value("${app.loan.lifecycle.system-user-uuid:${app.outbox.system-user-uuid:99999999-9999-9999-9999-999999999999}}")
            String systemUserUuid
    ) {
        this(loanRepositoryPort, Clock.systemDefaultZone(), parseSystemUserUuid(systemUserUuid));
    }

    public SolicitarPrestamoUseCase(LoanRepositoryPort loanRepositoryPort, Clock clock) {
        this(loanRepositoryPort, clock, DEFAULT_SYSTEM_USER_UUID);
    }

    public SolicitarPrestamoUseCase(LoanRepositoryPort loanRepositoryPort, Clock clock, UUID systemUserUuid) {
        this.loanRepositoryPort = loanRepositoryPort;
        this.clock = clock;
        this.systemUserUuid = systemUserUuid == null ? DEFAULT_SYSTEM_USER_UUID : systemUserUuid;
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
                throw new BadRequestException("LOAN_IMPLEMENT_INACTIVE", "El implemento seleccionado esta inactivo");
            }
        }

        List<UUID> implementUuids = requestedItems.stream()
                .map(LoanRequestedItem::implementUuid)
                .toList();

        validateStockAvailability(requestedItems, command.scheduledAt(), command.expectedReturnAt(), null);

        if (loanRepositoryPort.existsPendingLoanConflict(
                requesterUuid,
                command.scheduledAt(),
                command.expectedReturnAt(),
                implementUuids
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LOAN_DUPLICATE_REQUEST",
                    "Ya tienes una solicitud activa con uno o mas de estos implementos en la misma ventana horaria"
            );
        }

        LoanAggregate loan = loanRepositoryPort.createPendingLoan(
                new LoanCreateCommand(
                        requesterUuid,
                        systemUserUuid,
                        roomUuid,
                        subjectUuid,
                        command.scheduledAt(),
                        command.expectedReturnAt(),
                        normalizeOptionalText(command.notes()),
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
        validateCommandEnvelope(command);

        UUID requesterUuid = command.requesterUuid();
        UUID roomUuid = command.roomUuid();
        UUID subjectUuid = command.subjectUuid();
        LoanSummaryView currentLoan = requireEditableApprovedLoan(loanUuid, requesterUuid);

        validateCommand(command);

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
                throw new BadRequestException("LOAN_IMPLEMENT_INACTIVE", "El implemento seleccionado esta inactivo");
            }
        }

        List<UUID> implementUuids = requestedItems.stream()
                .map(LoanRequestedItem::implementUuid)
                .toList();

        validateStockAvailability(requestedItems, command.scheduledAt(), command.expectedReturnAt(), loanUuid);

        if (loanRepositoryPort.existsPendingLoanConflict(
                requesterUuid,
                loanUuid,
                command.scheduledAt(),
                command.expectedReturnAt(),
                implementUuids
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LOAN_DUPLICATE_REQUEST",
                    "Ya tienes una solicitud activa con uno o mas de estos implementos en la misma ventana horaria"
            );
        }

        LoanAggregate loan = loanRepositoryPort.updatePendingLoan(
                new LoanUpdateCommand(
                        currentLoan.uuid(),
                        requesterUuid,
                        roomUuid,
                        subjectUuid,
                        command.scheduledAt(),
                        command.expectedReturnAt(),
                        normalizeOptionalText(command.notes()),
                        requestedItems
                )
        );

        return loanRepositoryPort.findVisibleLoanSummaryByUuid(loan.uuid())
                .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado"));
    }

    private void validateCommand(SolicitarPrestamoCommand command) {
        validateCommandEnvelope(command);
        validateScheduledAt(command.scheduledAt());
        validateExpectedReturnAt(command.scheduledAt(), command.expectedReturnAt());
        validateNotes(command.notes());

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

    private void validateCommandEnvelope(SolicitarPrestamoCommand command) {
        if (command == null) {
            throw new BadRequestException("LOAN_REQUEST_INVALID", "La solicitud del prestamo es obligatoria");
        }
        if (command.requesterUuid() == null) {
            throw new BadRequestException("LOAN_REQUESTER_REQUIRED", "El solicitante autenticado es obligatorio");
        }
        if (command.roomUuid() == null) {
            throw new BadRequestException("LOAN_ROOM_REQUIRED", "room_uuid es obligatorio");
        }
    }

    private void validateNotes(String notes) {
        if (notes != null && notes.trim().length() > MAX_NOTES_LENGTH) {
            throw new BadRequestException("LOAN_NOTES_TOO_LONG", "notes no puede superar 1000 caracteres");
        }
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private LoanSummaryView requireEditableApprovedLoan(UUID loanUuid, UUID requesterUuid) {
        LoanSummaryView loan = loanRepositoryPort.findVisibleLoanSummaryByUuid(loanUuid)
                .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado"));

        if (!loan.requesterUuid().equals(requesterUuid)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOAN_UPDATE_FORBIDDEN", "No tienes permisos para modificar este prestamo");
        }
        if (loan.status() != LoanStatus.APPROVED) {
            throw new BadRequestException("LOAN_UPDATE_INVALID_STATE", "Solo se puede modificar un prestamo en estado approved");
        }
        return loan;
    }

    private void validateScheduledAt(OffsetDateTime scheduledAt) {
        if (scheduledAt == null) {
            throw new BadRequestException("LOAN_SCHEDULE_REQUIRED", "scheduled_at es obligatorio");
        }
        if (scheduledAt.isBefore(OffsetDateTime.now(clock))) {
            throw new BadRequestException(
                    "LOAN_SCHEDULE_PAST_NOT_ALLOWED",
                    "scheduled_at no puede estar en una fecha u hora pasada"
            );
        }
        validateScheduledAtRange(scheduledAt);
        validateScheduleWindow(
                scheduledAt,
                "LOAN_SCHEDULE_DAY_NOT_ALLOWED",
                "LOAN_SCHEDULE_TIME_NOT_ALLOWED",
                "scheduled_at"
        );
    }

    private void validateExpectedReturnAt(OffsetDateTime scheduledAt, OffsetDateTime expectedReturnAt) {
        if (expectedReturnAt == null) {
            return;
        }
        if (expectedReturnAt.isBefore(OffsetDateTime.now(clock))) {
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
        validateScheduleWindow(
                expectedReturnAt,
                "LOAN_EXPECTED_RETURN_DAY_NOT_ALLOWED",
                "LOAN_EXPECTED_RETURN_TIME_NOT_ALLOWED",
                "expected_return_at"
        );
    }

    private void validateScheduleWindow(
            OffsetDateTime value,
            String dayErrorCode,
            String timeErrorCode,
            String fieldName
    ) {
        if (value.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new BadRequestException(
                    dayErrorCode,
                    fieldName + " solo permite prestamos de lunes a sabado"
            );
        }

        LocalTime minTime = LocalTime.of(8, 0);
        LocalTime maxTime = LocalTime.of(22, 0);
        LocalTime selectedTime = value.toLocalTime();
        if (selectedTime.isBefore(minTime) || selectedTime.isAfter(maxTime)) {
            throw new BadRequestException(
                    timeErrorCode,
                    fieldName + " debe estar entre las 08:00 y las 22:00"
            );
        }
    }

    private void validateScheduledAtRange(OffsetDateTime scheduledAt) {
        LocalDate currentDate = OffsetDateTime.now(clock).toLocalDate();
        LocalDate latestAllowedDate = currentDate.plusDays(MAX_SCHEDULE_DAYS_AHEAD);
        LocalDate scheduledDate = scheduledAt.toLocalDate();
        if (scheduledDate.isAfter(latestAllowedDate)) {
            throw new BadRequestException(
                    "LOAN_SCHEDULE_RANGE_NOT_ALLOWED",
                    "scheduled_at solo permite solicitudes entre hoy y los proximos 14 dias corridos"
            );
        }
    }

    private void validateStockAvailability(
            List<LoanRequestedItem> requestedItems,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            UUID excludeLoanUuid
    ) {
        Map<UUID, LoanRequestedItemAvailability> availabilityByImplement = new HashMap<>();
        for (LoanRequestedItemAvailability availability : loanRepositoryPort.findRequestedItemAvailabilities(
                requestedItems.stream().map(LoanRequestedItem::implementUuid).toList(),
                scheduledAt,
                expectedReturnAt,
                excludeLoanUuid
        )) {
            availabilityByImplement.put(availability.implementUuid(), availability);
        }

        for (LoanRequestedItem item : requestedItems) {
            LoanRequestedItemAvailability availability = availabilityByImplement.get(item.implementUuid());
            if (availability == null) {
                continue;
            }
            if (item.requestedQuantity() > availability.availableQuantity()) {
                throw new ConflictException(
                        "LOAN_STOCK_CONFLICT",
                        String.format(
                                "Solo puedes solicitar dentro del stock disponible. %s tiene %d unidad(es) disponibles para esta solicitud.",
                                availability.implementName(),
                                availability.availableQuantity()
                        )
                );
            }
        }
    }

    private static UUID parseSystemUserUuid(String rawSystemUserUuid) {
        if (rawSystemUserUuid == null || rawSystemUserUuid.isBlank()) {
            return DEFAULT_SYSTEM_USER_UUID;
        }
        return UUID.fromString(rawSystemUserUuid.trim());
    }
}

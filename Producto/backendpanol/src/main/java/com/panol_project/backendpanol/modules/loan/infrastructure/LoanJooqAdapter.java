package com.panol_project.backendpanol.modules.loan.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.Individual.INDIVIDUAL;
import static com.panol_project.backendpanol.jooq.tables.Loan.LOAN;
import static com.panol_project.backendpanol.jooq.tables.LoanDetail.LOAN_DETAIL;
import static com.panol_project.backendpanol.jooq.tables.LoanDetailIndividual.LOAN_DETAIL_INDIVIDUAL;
import static com.panol_project.backendpanol.jooq.tables.LoanStatusHistory.LOAN_STATUS_HISTORY;
import static com.panol_project.backendpanol.jooq.tables.Room.ROOM;
import static com.panol_project.backendpanol.jooq.tables.Stock.STOCK;
import static com.panol_project.backendpanol.jooq.tables.Subject.SUBJECT;
import static com.panol_project.backendpanol.jooq.tables.User.USER;
import static com.panol_project.backendpanol.jooq.tables.VLoanStateDates.V_LOAN_STATE_DATES;
import static com.panol_project.backendpanol.jooq.tables.VLoanStatusTimeline.V_LOAN_STATUS_TIMELINE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.jooq.enums.IndividualAllocationStatusEnum;
import com.panol_project.backendpanol.jooq.enums.IndividualStatusEnum;
import com.panol_project.backendpanol.jooq.enums.ItemTypeEnum;
import com.panol_project.backendpanol.jooq.enums.LoanStatusEnum;
import com.panol_project.backendpanol.jooq.enums.ReturnConditionEnum;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCancelCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanCompleteCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanDetailItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanImplementAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItemAvailability;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnConsumableItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnIndividual;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewDecision;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanStateDatesView;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatus;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatusTimelineEntry;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryPage;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.modules.loan.domain.LoanUpdateCommand;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.ConflictException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class LoanJooqAdapter implements LoanRepositoryPort {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public LoanJooqAdapter(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsActiveRequesterByUuid(UUID requesterUuid) {
        if (requesterUuid == null) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(USER)
                        .where(USER.UUID.eq(requesterUuid).and(USER.ACTIVE.isTrue()))
        );
    }

    @Override
    public boolean existsActiveRoomByUuid(UUID roomUuid) {
        if (roomUuid == null) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ROOM)
                        .where(ROOM.UUID.eq(roomUuid).and(ROOM.ACTIVE.isTrue()))
        );
    }

    @Override
    public boolean existsActiveSubjectByUuid(UUID subjectUuid) {
        if (subjectUuid == null) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(SUBJECT)
                        .where(SUBJECT.UUID.eq(subjectUuid).and(SUBJECT.ACTIVE.isTrue()))
        );
    }

    @Override
    public Optional<LoanImplementAvailability> findImplementAvailabilityByUuid(UUID implementUuid) {
        if (implementUuid == null) {
            return Optional.empty();
        }
        return dsl.select(IMPLEMENT.UUID, IMPLEMENT.ACTIVE)
                        .from(IMPLEMENT)
                        .where(IMPLEMENT.UUID.eq(implementUuid))
                        .fetchOptional(record -> new LoanImplementAvailability(
                                record.get(IMPLEMENT.UUID),
                                Boolean.TRUE.equals(record.get(IMPLEMENT.ACTIVE))
                        ));
    }

    @Override
    public List<LoanRequestedItemAvailability> findRequestedItemAvailabilities(
            List<UUID> implementUuids,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            UUID excludeLoanUuid
    ) {
        if (implementUuids == null || implementUuids.isEmpty() || scheduledAt == null) {
            return List.of();
        }

        List<UUID> filteredImplementUuids = implementUuids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (filteredImplementUuids.isEmpty()) {
            return List.of();
        }

        Long excludeLoanId = findLoanIdByUuid(excludeLoanUuid);
        OffsetDateTime effectiveExpectedReturnAt = resolveExpectedReturnAt(scheduledAt, expectedReturnAt);

        return dsl.select(IMPLEMENT.ID, IMPLEMENT.UUID, IMPLEMENT.NAME, IMPLEMENT.ACTIVE)
                .from(IMPLEMENT)
                .where(IMPLEMENT.UUID.in(filteredImplementUuids))
                .fetch(record -> {
                    Record availabilityRecord = dsl.fetchOne(
                            "select * from public.fn_get_implement_availability(?::bigint, ?::timestamptz, ?::timestamptz, false, ?::bigint)",
                            record.get(IMPLEMENT.ID),
                            scheduledAt,
                            effectiveExpectedReturnAt,
                            excludeLoanId
                    );

                    int availableQuantity = 0;
                    if (availabilityRecord != null) {
                        Integer rawAvailable = availabilityRecord.get("available_quantity", Integer.class);
                        if (rawAvailable != null) {
                            availableQuantity = rawAvailable;
                        }
                    }

                    return new LoanRequestedItemAvailability(
                            record.get(IMPLEMENT.UUID),
                            record.get(IMPLEMENT.NAME),
                            Boolean.TRUE.equals(record.get(IMPLEMENT.ACTIVE)),
                            availableQuantity
                    );
                });
    }

    @Override
    public boolean existsPendingLoanConflict(
            UUID requesterUuid,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            List<UUID> implementUuids
    ) {
        return existsPendingLoanConflict(requesterUuid, null, scheduledAt, expectedReturnAt, implementUuids);
    }

    @Override
    public boolean existsPendingLoanConflict(
            UUID requesterUuid,
            UUID excludeLoanUuid,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            List<UUID> implementUuids
    ) {
        if (requesterUuid == null || scheduledAt == null || implementUuids == null || implementUuids.isEmpty()) {
            return false;
        }

        Long requesterId = findActiveUserIdByUuid(requesterUuid);
        if (requesterId == null) {
            return false;
        }

        List<UUID> filteredImplementUuids = implementUuids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (filteredImplementUuids.isEmpty()) {
            return false;
        }

        OffsetDateTime effectiveExpectedReturnAt = resolveExpectedReturnAt(scheduledAt, expectedReturnAt);

        Condition condition = LOAN.REQUESTER_ID.eq(requesterId)
                .and(LOAN.STATUS.eq(LoanStatusEnum.pending))
                .and(IMPLEMENT.UUID.in(filteredImplementUuids))
                .and(LOAN.SCHEDULED_AT.lt(effectiveExpectedReturnAt))
                .and(LOAN.EXPECTED_RETURN_AT.gt(scheduledAt));
        if (excludeLoanUuid != null) {
            condition = condition.and(LOAN.UUID.ne(excludeLoanUuid));
        }

        return dsl.fetchExists(
                dsl.selectOne()
                        .from(LOAN)
                        .join(LOAN_DETAIL).on(LOAN_DETAIL.LOAN_ID.eq(LOAN.ID))
                        .join(IMPLEMENT).on(IMPLEMENT.ID.eq(LOAN_DETAIL.IMPLEMENT_ID))
                        .where(condition)
        );
    }

    @Override
    public LoanAggregate createPendingLoan(LoanCreateCommand command) {
        Long requesterId = requireUserIdByUuid(command.requesterUuid());
        Long roomId = findRoomIdByUuid(command.roomUuid());
        Long subjectId = findSubjectIdByUuid(command.subjectUuid());

        OffsetDateTime now = OffsetDateTime.now();
        UUID loanUuid = UUID.randomUUID();

        var insertedLoan = dsl.insertInto(LOAN)
                .set(LOAN.UUID, loanUuid)
                .set(LOAN.REQUESTER_ID, requesterId)
                .set(LOAN.ROOM_ID, roomId)
                .set(LOAN.SUBJECT_ID, subjectId)
                .set(LOAN.STATUS, LoanStatusEnum.pending)
                .set(LOAN.SCHEDULED_AT, command.scheduledAt())
                .set(LOAN.EXPECTED_RETURN_AT, resolveExpectedReturnAt(command.scheduledAt(), command.expectedReturnAt()))
                .set(LOAN.CREATED_AT, now)
                .returning(LOAN.ID, LOAN.STATUS, LOAN.CREATED_AT, LOAN.UUID)
                .fetchOne();

        if (insertedLoan == null) {
            throw new IllegalStateException("No fue posible crear el prestamo");
        }

        Long loanId = insertedLoan.getId();
        List<LoanDetailItem> details = insertLoanDetails(loanId, command.requestedItems());

        dsl.insertInto(LOAN_STATUS_HISTORY)
                .set(LOAN_STATUS_HISTORY.LOAN_ID, loanId)
                .set(LOAN_STATUS_HISTORY.ACTOR_USER_ID, requesterId)
                .set(LOAN_STATUS_HISTORY.FROM_STATUS, (LoanStatusEnum) null)
                .set(LOAN_STATUS_HISTORY.TO_STATUS, LoanStatusEnum.pending)
                .set(LOAN_STATUS_HISTORY.NOTES, resolveStatusNotes("Solicitud creada", command.notes()))
                .set(LOAN_STATUS_HISTORY.CHANGED_AT, now)
                .execute();

        dsl.fetch("select public.fn_notify_new_loan_request(?::uuid, ?::uuid)", command.requesterUuid(), loanUuid);

        return new LoanAggregate(
                insertedLoan.getUuid(),
                command.requesterUuid(),
                command.roomUuid(),
                command.subjectUuid(),
                toDomainStatus(insertedLoan.getStatus()),
                command.scheduledAt(),
                resolveExpectedReturnAt(command.scheduledAt(), command.expectedReturnAt()),
                insertedLoan.getCreatedAt(),
                details
        );
    }

    @Override
    public LoanAggregate updatePendingLoan(LoanUpdateCommand command) {
        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.PENDING) {
            throw new BadRequestException("LOAN_UPDATE_INVALID_STATE", "Solo se puede modificar un prestamo en estado pending");
        }
        if (!Objects.equals(current.requesterUuid(), command.requesterUuid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOAN_UPDATE_FORBIDDEN", "No tienes permisos para modificar este prestamo");
        }

        Long roomId = findRoomIdByUuid(command.roomUuid());
        Long subjectId = findSubjectIdByUuid(command.subjectUuid());

        dsl.update(LOAN)
                .set(LOAN.ROOM_ID, roomId)
                .set(LOAN.SUBJECT_ID, subjectId)
                .set(LOAN.SCHEDULED_AT, command.scheduledAt())
                .set(LOAN.EXPECTED_RETURN_AT, resolveExpectedReturnAt(command.scheduledAt(), command.expectedReturnAt()))
                .where(LOAN.ID.eq(current.loanId()))
                .execute();

        dsl.deleteFrom(LOAN_DETAIL_INDIVIDUAL)
                .where(LOAN_DETAIL_INDIVIDUAL.LOAN_ID.eq(current.loanId()))
                .execute();
        dsl.deleteFrom(LOAN_DETAIL)
                .where(LOAN_DETAIL.LOAN_ID.eq(current.loanId()))
                .execute();

        List<LoanDetailItem> details = insertLoanDetails(current.loanId(), command.requestedItems());

        dsl.insertInto(LOAN_STATUS_HISTORY)
                .set(LOAN_STATUS_HISTORY.LOAN_ID, current.loanId())
                .set(LOAN_STATUS_HISTORY.ACTOR_USER_ID, requireUserIdByUuid(command.requesterUuid()))
                .set(LOAN_STATUS_HISTORY.FROM_STATUS, LoanStatusEnum.pending)
                .set(LOAN_STATUS_HISTORY.TO_STATUS, LoanStatusEnum.pending)
                .set(LOAN_STATUS_HISTORY.NOTES, resolveStatusNotes("Solicitud modificada por docente", command.notes()))
                .set(LOAN_STATUS_HISTORY.CHANGED_AT, OffsetDateTime.now())
                .execute();

        dsl.fetch(
                "select public.fn_notify_pending_loan_updated(?::uuid, ?::uuid)",
                command.requesterUuid(),
                current.loanUuid()
        );

        return new LoanAggregate(
                current.loanUuid(),
                command.requesterUuid(),
                command.roomUuid(),
                command.subjectUuid(),
                LoanStatus.PENDING,
                command.scheduledAt(),
                resolveExpectedReturnAt(command.scheduledAt(), command.expectedReturnAt()),
                current.createdAt(),
                details
        );
    }

    @Override
    public Optional<LoanSummaryView> findVisibleLoanSummaryByUuid(UUID loanUuid) {
        List<LoanSummaryRow> rows = fetchLoanSummaryRows(loanUuid, null, null, null);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        LoanSummaryRow row = rows.getFirst();
        Map<Long, List<LoanSummaryView.ItemView>> itemsByLoanId = fetchLoanSummaryItems(List.of(row.loanId()));
        return Optional.of(toSummaryView(row, itemsByLoanId.getOrDefault(row.loanId(), List.of())));
    }

    @Override
    public LoanSummaryPage findVisibleLoanSummaries(UUID requesterUuid, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int offset = (safePage - 1) * safeSize;

        long totalItems = countLoanSummaryRows(requesterUuid);
        int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / safeSize);
        if (safePage > totalPages) {
            safePage = totalPages;
            offset = (safePage - 1) * safeSize;
        }

        List<LoanSummaryRow> rows = fetchLoanSummaryRows(null, requesterUuid, safeSize, offset);
        if (rows.isEmpty()) {
            return new LoanSummaryPage(List.of(), safePage, safeSize, totalItems, totalPages);
        }

        Map<Long, List<LoanSummaryView.ItemView>> itemsByLoanId = fetchLoanSummaryItems(rows.stream().map(LoanSummaryRow::loanId).toList());
        List<LoanSummaryView> items = rows.stream()
                .map(row -> toSummaryView(row, itemsByLoanId.getOrDefault(row.loanId(), List.of())))
                .toList();
        return new LoanSummaryPage(items, safePage, safeSize, totalItems, totalPages);
    }

    @Override
    public List<LoanSummaryView> findAllVisibleLoanSummaries() {
        List<LoanSummaryRow> rows = fetchLoanSummaryRows(null, null, null, null);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LoanSummaryView.ItemView>> itemsByLoanId = fetchLoanSummaryItems(rows.stream().map(LoanSummaryRow::loanId).toList());
        return rows.stream()
                .map(row -> toSummaryView(row, itemsByLoanId.getOrDefault(row.loanId(), List.of())))
                .toList();
    }

    @Override
    public LoanAggregate reviewLoan(LoanReviewCommand command) {
        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.PENDING) {
            throw new BadRequestException("LOAN_REVIEW_INVALID_STATE", "Solo se pueden revisar prestamos en estado pending");
        }

        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        String notes = normalizeOptionalText(command.notes());

        if (command.decision() == LoanReviewDecision.APPROVE) {
            if (command.items() == null || command.items().isEmpty()) {
                callApproveLoanFunction(current.loanId(), actorUserId, notes);
            } else {
                approveLoanWithAdjustedQuantities(current, actorUserId, notes, command.items());
            }
        } else if (command.decision() == LoanReviewDecision.REJECT) {
            if (notes == null) {
                throw new BadRequestException("LOAN_REJECTION_NOTES_REQUIRED", "notes es obligatorio al rechazar");
            }
            callLoanStatusChangeFunction(current.loanId(), actorUserId, LoanStatusEnum.rejected, notes);
        } else {
            throw new BadRequestException("LOAN_REVIEW_DECISION_INVALID", "decision invalida");
        }

        return loadLoanAggregateById(current.loanId());
    }

    private void approveLoanWithAdjustedQuantities(
            LoanRow current,
            Long actorUserId,
            String notes,
            List<LoanReviewItem> requestedItems
    ) {
        dsl.fetch(
                "select 1 from public.stock s join public.loan_detail ld on ld.implement_id = s.implement_id where ld.loan_id = ?::bigint for update of s",
                current.loanId()
        );

        Map<UUID, LoanDetailContext> detailByImplementUuid = fetchLoanDetailContextByImplementUuid(current.loanId());
        Map<UUID, LoanReviewItem> requestedByImplementUuid = toReviewItemMap(requestedItems);
        int totalApprovedQuantity = 0;

        for (LoanReviewItem item : requestedByImplementUuid.values()) {
            if (!detailByImplementUuid.containsKey(item.implementUuid())) {
                throw new BadRequestException(
                        "LOAN_REVIEW_IMPLEMENT_INVALID",
                        "Solo puedes aprobar cantidades para implementos incluidos en la solicitud"
                );
            }
        }

        for (LoanDetailContext detail : detailByImplementUuid.values()) {
            LoanReviewItem requestedItem = requestedByImplementUuid.get(detail.implementUuid());
            int approvedQuantity = requestedItem == null
                    ? detail.requestedQuantity()
                    : requestedItem.approvedQuantity();

            if (approvedQuantity > detail.requestedQuantity()) {
                throw new BadRequestException(
                        "LOAN_REVIEW_QUANTITY_EXCEEDS_REQUESTED",
                        "La cantidad aprobada no puede superar la cantidad solicitada"
                );
            }

            validateApprovalAvailability(current, detail, approvedQuantity);

            dsl.update(LOAN_DETAIL)
                    .set(LOAN_DETAIL.RESERVED_QUANTITY, approvedQuantity)
                    .where(
                            LOAN_DETAIL.LOAN_ID.eq(current.loanId())
                                    .and(LOAN_DETAIL.IMPLEMENT_ID.eq(detail.implementId()))
                    )
                    .execute();

            totalApprovedQuantity += approvedQuantity;
        }

        if (totalApprovedQuantity <= 0) {
            throw new BadRequestException(
                    "LOAN_REVIEW_EMPTY_APPROVAL",
                    "Debes aprobar al menos una unidad para continuar"
            );
        }

        callLoanStatusChangeFunction(
                current.loanId(),
                actorUserId,
                LoanStatusEnum.approved,
                resolveStatusNotes("Solicitud aprobada por disponibilidad.", notes)
        );
    }

    @Override
    public LoanAggregate cancelLoan(LoanCancelCommand command) {
        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        callCancelLoanFunction(current.loanId(), actorUserId, normalizeOptionalText(command.notes()));
        return loadLoanAggregateById(current.loanId());
    }

    @Override
    public LoanDeliveryResult deliverLoan(LoanDeliveryCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new BadRequestException("LOAN_DELIVERY_ITEMS_REQUIRED", "Debes incluir items para registrar la entrega");
        }

        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.APPROVED && current.status() != LoanStatus.PREPARED) {
            throw new BadRequestException(
                    "LOAN_DELIVERY_INVALID_STATE",
                    "Solo se puede entregar un prestamo en estado approved o prepared"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        alignDefaultExpectedReturnAtAfterDelivery(current, now);

        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        Map<UUID, LoanDetailContext> detailByImplementUuid = fetchLoanDetailContextByImplementUuid(current.loanId());
        Map<UUID, LoanDeliveryItem> requestedItems = toDeliveryItemMap(command.items());
        addAdditionalDeliveryDetails(current.loanId(), requestedItems, detailByImplementUuid);

        for (LoanDetailContext detailContext : detailByImplementUuid.values()) {
            LoanDeliveryItem requestedItem = requestedItems.get(detailContext.implementUuid());
            int deliveryQuantity = resolveRequestedDeliveryQuantity(detailContext, requestedItem);

            if (detailContext.itemType() == ItemTypeEnum.individual) {
                assignIndividualsForDelivery(current.loanId(), detailContext, requestedItem, deliveryQuantity);
            }

            if (current.status() == LoanStatus.PREPARED) {
                syncPreparedReservationDifference(detailContext, deliveryQuantity);
            }

            updateLoanDetailQuantitiesForDelivery(current.loanId(), detailContext, deliveryQuantity);
        }

        if (current.status() == LoanStatus.APPROVED) {
            callPrepareLoanFunction(current.loanId(), actorUserId, "Preparado para entrega");
        }
        callDeliverLoanFunction(current.loanId(), actorUserId, resolveStatusNotes("Entrega registrada en panol", command.notes()));

        return new LoanDeliveryResult(loadLoanAggregateById(current.loanId()));
    }

    @Override
    public LoanReturnResult completeLoan(LoanCompleteCommand command) {
        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.DELIVERED && current.status() != LoanStatus.OVERDUE) {
            throw new BadRequestException(
                    "LOAN_COMPLETE_INVALID_STATE",
                    "Solo se puede completar un prestamo en estado delivered o overdue"
            );
        }

        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        List<LoanReturnPayloadItem> payloadItems = buildFullReturnAsGoodPayload(current.loanId());
        callCompleteLoanFunction(
                current.loanId(),
                actorUserId,
                resolveStatusNotes("Prestamo completado por coordinador", command.notes()),
                payloadItems
        );

        return new LoanReturnResult(loadLoanAggregateById(current.loanId()));
    }

    private void alignDefaultExpectedReturnAtAfterDelivery(LoanRow current, OffsetDateTime deliveredAt) {
        if (current.scheduledAt() == null || current.expectedReturnAt() == null) {
            return;
        }

        OffsetDateTime defaultExpectedReturnAt = current.scheduledAt().plusHours(2);
        if (!current.expectedReturnAt().toInstant().equals(defaultExpectedReturnAt.toInstant())) {
            return;
        }

        OffsetDateTime recalculatedExpectedReturnAt = deliveredAt.plusHours(2);
        if (!recalculatedExpectedReturnAt.isAfter(current.scheduledAt())) {
            recalculatedExpectedReturnAt = current.scheduledAt().plusMinutes(1);
        }

        dsl.update(LOAN)
                .set(LOAN.EXPECTED_RETURN_AT, recalculatedExpectedReturnAt)
                .where(LOAN.ID.eq(current.loanId()))
                .execute();
    }

    @Override
    public LoanReturnResult returnLoan(LoanReturnCommand command) {
        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.DELIVERED && current.status() != LoanStatus.OVERDUE) {
            throw new BadRequestException(
                    "LOAN_RETURN_INVALID_STATE",
                    "Solo se puede devolver un prestamo en estado delivered o overdue"
            );
        }

        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        List<LoanReturnPayloadItem> payloadItems = buildReturnPayloadFromCommand(current.loanId(), command);
        callCompleteLoanFunction(
                current.loanId(),
                actorUserId,
                resolveStatusNotes("Retorno completo registrado", command.notes()),
                payloadItems
        );

        return new LoanReturnResult(loadLoanAggregateById(current.loanId()));
    }

    @Override
    public Optional<LoanStateDatesView> findLoanStateDatesByUuid(UUID loanUuid) {
        Record record = dsl.select(
                        V_LOAN_STATE_DATES.APPROVED_AT,
                        V_LOAN_STATE_DATES.PREPARED_AT,
                        V_LOAN_STATE_DATES.DELIVERED_AT,
                        V_LOAN_STATE_DATES.COMPLETED_AT,
                        V_LOAN_STATE_DATES.REJECTED_AT,
                        V_LOAN_STATE_DATES.CANCELLED_AT,
                        V_LOAN_STATE_DATES.EXPIRED_AT,
                        V_LOAN_STATE_DATES.OVERDUE_AT
                )
                .from(LOAN)
                .leftJoin(V_LOAN_STATE_DATES).on(V_LOAN_STATE_DATES.LOAN_ID.eq(LOAN.ID))
                .where(LOAN.UUID.eq(loanUuid))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(new LoanStateDatesView(
                record.get(V_LOAN_STATE_DATES.APPROVED_AT),
                record.get(V_LOAN_STATE_DATES.PREPARED_AT),
                record.get(V_LOAN_STATE_DATES.DELIVERED_AT),
                record.get(V_LOAN_STATE_DATES.COMPLETED_AT),
                record.get(V_LOAN_STATE_DATES.REJECTED_AT),
                record.get(V_LOAN_STATE_DATES.CANCELLED_AT),
                record.get(V_LOAN_STATE_DATES.EXPIRED_AT),
                record.get(V_LOAN_STATE_DATES.OVERDUE_AT)
        ));
    }

    @Override
    public List<LoanStatusTimelineEntry> findLoanStatusTimelineByUuid(UUID loanUuid) {
        return dsl.selectFrom(V_LOAN_STATUS_TIMELINE)
                .where(V_LOAN_STATUS_TIMELINE.LOAN_UUID.eq(loanUuid))
                .orderBy(V_LOAN_STATUS_TIMELINE.CHANGED_AT.asc(), V_LOAN_STATUS_TIMELINE.HISTORY_ID.asc())
                .fetch(record -> new LoanStatusTimelineEntry(
                        record.get(V_LOAN_STATUS_TIMELINE.HISTORY_ID),
                        toDomainStatusNullable(record.get(V_LOAN_STATUS_TIMELINE.FROM_STATUS)),
                        toDomainStatus(record.get(V_LOAN_STATUS_TIMELINE.TO_STATUS)),
                        record.get(V_LOAN_STATUS_TIMELINE.ACTOR_USER_ID),
                        record.get(V_LOAN_STATUS_TIMELINE.ACTOR_NAME),
                        record.get(V_LOAN_STATUS_TIMELINE.ACTOR_EMAIL),
                        record.get(V_LOAN_STATUS_TIMELINE.NOTES),
                        record.get(V_LOAN_STATUS_TIMELINE.CHANGED_AT)
                ));
    }

    @Override
    public int markOverdueLoans(UUID actorUuid, OffsetDateTime currentTime) {
        Long actorUserId = requireUserIdByUuid(actorUuid);
        return callMarkOverdueLoansFunction(actorUserId, currentTime == null ? OffsetDateTime.now() : currentTime);
    }

    @Override
    public int expirePendingLoans(UUID actorUuid, OffsetDateTime currentTime, int graceMinutes) {
        Long actorUserId = requireUserIdByUuid(actorUuid);
        return callExpirePendingLoansFunction(
                actorUserId,
                currentTime == null ? OffsetDateTime.now() : currentTime,
                Math.max(0, graceMinutes)
        );
    }

    private LoanAggregate loadLoanAggregateById(Long loanId) {
        List<LoanRow> rows = fetchLoanRows(loanId);
        if (rows.isEmpty()) {
            throw new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado");
        }
        LoanRow row = rows.getFirst();
        Map<Long, List<LoanDetailItem>> detailsByLoanId = fetchLoanDetails(List.of(row.loanId()));
        return toAggregate(row, detailsByLoanId.getOrDefault(row.loanId(), List.of()));
    }

    private List<LoanSummaryRow> fetchLoanSummaryRows(UUID onlyLoanUuid, UUID requesterUuid, Integer limit, Integer offset) {
        Field<UUID> requesterUuidField = USER.UUID.as("requester_uuid");
        Field<UUID> roomUuidField = ROOM.UUID.as("room_uuid");
        Field<String> roomNameField = ROOM.NAME.as("room_name");
        Field<UUID> subjectUuidField = SUBJECT.UUID.as("subject_uuid");
        Field<String> subjectNameField = SUBJECT.NAME.as("subject_name");
        Condition condition = buildLoanSummaryCondition(onlyLoanUuid, requesterUuid);

        var query = dsl.select(
                        LOAN.ID,
                        LOAN.UUID,
                        requesterUuidField,
                        LOAN.STATUS,
                        LOAN.SCHEDULED_AT,
                        LOAN.EXPECTED_RETURN_AT,
                        LOAN.CREATED_AT,
                        roomUuidField,
                        roomNameField,
                        subjectUuidField,
                        subjectNameField
                )
                .from(LOAN)
                .join(USER).on(USER.ID.eq(LOAN.REQUESTER_ID))
                .leftJoin(ROOM).on(ROOM.ID.eq(LOAN.ROOM_ID))
                .leftJoin(SUBJECT).on(SUBJECT.ID.eq(LOAN.SUBJECT_ID))
                .where(condition)
                .orderBy(LOAN.CREATED_AT.desc(), LOAN.ID.desc());

        if (limit != null && offset != null) {
            return query.limit(limit).offset(offset).fetch(record -> new LoanSummaryRow(
                    record.get(LOAN.ID),
                    record.get(LOAN.UUID),
                    record.get(requesterUuidField),
                    toDomainStatus(record.get(LOAN.STATUS)),
                    record.get(LOAN.SCHEDULED_AT),
                    record.get(LOAN.EXPECTED_RETURN_AT),
                    record.get(LOAN.CREATED_AT),
                    record.get(roomUuidField),
                    record.get(roomNameField),
                    record.get(subjectUuidField),
                    record.get(subjectNameField)
            ));
        }
        if (limit != null) {
            return query.limit(limit).fetch(record -> new LoanSummaryRow(
                    record.get(LOAN.ID),
                    record.get(LOAN.UUID),
                    record.get(requesterUuidField),
                    toDomainStatus(record.get(LOAN.STATUS)),
                    record.get(LOAN.SCHEDULED_AT),
                    record.get(LOAN.EXPECTED_RETURN_AT),
                    record.get(LOAN.CREATED_AT),
                    record.get(roomUuidField),
                    record.get(roomNameField),
                    record.get(subjectUuidField),
                    record.get(subjectNameField)
            ));
        }
        if (offset != null) {
            return query.offset(offset).fetch(record -> new LoanSummaryRow(
                    record.get(LOAN.ID),
                    record.get(LOAN.UUID),
                    record.get(requesterUuidField),
                    toDomainStatus(record.get(LOAN.STATUS)),
                    record.get(LOAN.SCHEDULED_AT),
                    record.get(LOAN.EXPECTED_RETURN_AT),
                    record.get(LOAN.CREATED_AT),
                    record.get(roomUuidField),
                    record.get(roomNameField),
                    record.get(subjectUuidField),
                    record.get(subjectNameField)
            ));
        }

        return query.fetch(record -> new LoanSummaryRow(
                        record.get(LOAN.ID),
                        record.get(LOAN.UUID),
                        record.get(requesterUuidField),
                        toDomainStatus(record.get(LOAN.STATUS)),
                        record.get(LOAN.SCHEDULED_AT),
                        record.get(LOAN.EXPECTED_RETURN_AT),
                        record.get(LOAN.CREATED_AT),
                        record.get(roomUuidField),
                        record.get(roomNameField),
                        record.get(subjectUuidField),
                        record.get(subjectNameField)
                ));
    }

    private long countLoanSummaryRows(UUID requesterUuid) {
        Condition condition = buildLoanSummaryCondition(null, requesterUuid);
        Integer count = dsl.selectCount()
                .from(LOAN)
                .join(USER).on(USER.ID.eq(LOAN.REQUESTER_ID))
                .leftJoin(ROOM).on(ROOM.ID.eq(LOAN.ROOM_ID))
                .leftJoin(SUBJECT).on(SUBJECT.ID.eq(LOAN.SUBJECT_ID))
                .where(condition)
                .fetchOne(0, Integer.class);
        return count == null ? 0L : count.longValue();
    }

    private Condition buildLoanSummaryCondition(UUID onlyLoanUuid, UUID requesterUuid) {
        Condition condition = DSL.trueCondition();
        if (onlyLoanUuid != null) {
            condition = condition.and(LOAN.UUID.eq(onlyLoanUuid));
        }
        if (requesterUuid != null) {
            condition = condition.and(USER.UUID.eq(requesterUuid));
        }
        return condition;
    }

    private LoanRow requireLoanRowForUpdate(UUID loanUuid) {
        Field<UUID> requesterUuidField = USER.UUID.as("requester_uuid");
        Field<UUID> roomUuidField = ROOM.UUID.as("room_uuid");
        Field<UUID> subjectUuidField = SUBJECT.UUID.as("subject_uuid");

        Record record = dsl.select(
                        LOAN.ID,
                        LOAN.UUID,
                        requesterUuidField,
                        roomUuidField,
                        subjectUuidField,
                        LOAN.STATUS,
                        LOAN.SCHEDULED_AT,
                        LOAN.EXPECTED_RETURN_AT,
                        LOAN.CREATED_AT
                )
                .from(LOAN)
                .join(USER).on(USER.ID.eq(LOAN.REQUESTER_ID))
                .leftJoin(ROOM).on(ROOM.ID.eq(LOAN.ROOM_ID))
                .leftJoin(SUBJECT).on(SUBJECT.ID.eq(LOAN.SUBJECT_ID))
                .where(LOAN.UUID.eq(loanUuid))
                // Lock only the loan row. Postgres rejects FOR UPDATE over nullable outer-joined tables.
                .forUpdate().of(LOAN)
                .fetchOne();

        if (record == null) {
            throw new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado");
        }

        return new LoanRow(
                record.get(LOAN.ID),
                record.get(LOAN.UUID),
                record.get(requesterUuidField),
                record.get(roomUuidField),
                record.get(subjectUuidField),
                toDomainStatus(record.get(LOAN.STATUS)),
                record.get(LOAN.SCHEDULED_AT),
                record.get(LOAN.EXPECTED_RETURN_AT),
                record.get(LOAN.CREATED_AT)
        );
    }

    private List<LoanRow> fetchLoanRows(Long onlyLoanId) {
        Field<UUID> requesterUuidField = USER.UUID.as("requester_uuid");
        Field<UUID> roomUuidField = ROOM.UUID.as("room_uuid");
        Field<UUID> subjectUuidField = SUBJECT.UUID.as("subject_uuid");
        Condition condition = DSL.trueCondition();

        if (onlyLoanId != null) {
            condition = condition.and(LOAN.ID.eq(onlyLoanId));
        }

        return dsl.select(
                        LOAN.ID,
                        LOAN.UUID,
                        requesterUuidField,
                        roomUuidField,
                        subjectUuidField,
                        LOAN.STATUS,
                        LOAN.SCHEDULED_AT,
                        LOAN.EXPECTED_RETURN_AT,
                        LOAN.CREATED_AT
                )
                .from(LOAN)
                .join(USER).on(USER.ID.eq(LOAN.REQUESTER_ID))
                .leftJoin(ROOM).on(ROOM.ID.eq(LOAN.ROOM_ID))
                .leftJoin(SUBJECT).on(SUBJECT.ID.eq(LOAN.SUBJECT_ID))
                .where(condition)
                .orderBy(LOAN.CREATED_AT.desc(), LOAN.ID.desc())
                .fetch(record -> new LoanRow(
                        record.get(LOAN.ID),
                        record.get(LOAN.UUID),
                        record.get(requesterUuidField),
                        record.get(roomUuidField),
                        record.get(subjectUuidField),
                        toDomainStatus(record.get(LOAN.STATUS)),
                        record.get(LOAN.SCHEDULED_AT),
                        record.get(LOAN.EXPECTED_RETURN_AT),
                        record.get(LOAN.CREATED_AT)
                ));
    }

    private Map<Long, List<LoanSummaryView.ItemView>> fetchLoanSummaryItems(List<Long> loanIds) {
        if (loanIds == null || loanIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<LoanSummaryView.ItemView>> itemsByLoanId = new HashMap<>();
        dsl.select(
                        LOAN_DETAIL.LOAN_ID,
                        IMPLEMENT.UUID,
                        IMPLEMENT.NAME,
                        LOAN_DETAIL.REQUESTED_QUANTITY,
                        LOAN_DETAIL.RESERVED_QUANTITY,
                        LOAN_DETAIL.DELIVERED_QUANTITY
                )
                .from(LOAN_DETAIL)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(LOAN_DETAIL.IMPLEMENT_ID))
                .where(LOAN_DETAIL.LOAN_ID.in(loanIds))
                .orderBy(LOAN_DETAIL.LOAN_ID.asc(), IMPLEMENT.UUID.asc())
                .fetch(record -> {
                    Long loanId = record.get(LOAN_DETAIL.LOAN_ID);
                    LoanSummaryView.ItemView item = new LoanSummaryView.ItemView(
                            record.get(IMPLEMENT.UUID),
                            record.get(IMPLEMENT.NAME),
                            record.get(LOAN_DETAIL.REQUESTED_QUANTITY),
                            record.get(LOAN_DETAIL.RESERVED_QUANTITY),
                            record.get(LOAN_DETAIL.DELIVERED_QUANTITY)
                    );
                    itemsByLoanId.computeIfAbsent(loanId, ignored -> new ArrayList<>()).add(item);
                    return null;
                });

        return itemsByLoanId;
    }

    private Map<Long, List<LoanDetailItem>> fetchLoanDetails(List<Long> loanIds) {
        if (loanIds == null || loanIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<LoanDetailItem>> detailsByLoanId = new HashMap<>();
        dsl.select(
                        LOAN_DETAIL.LOAN_ID,
                        IMPLEMENT.UUID,
                        LOAN_DETAIL.REQUESTED_QUANTITY,
                        LOAN_DETAIL.RESERVED_QUANTITY,
                        LOAN_DETAIL.DELIVERED_QUANTITY
                )
                .from(LOAN_DETAIL)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(LOAN_DETAIL.IMPLEMENT_ID))
                .where(LOAN_DETAIL.LOAN_ID.in(loanIds))
                .orderBy(LOAN_DETAIL.LOAN_ID.asc(), IMPLEMENT.UUID.asc())
                .fetch(record -> {
                    Long loanId = record.get(LOAN_DETAIL.LOAN_ID);
                    LoanDetailItem item = new LoanDetailItem(
                            record.get(IMPLEMENT.UUID),
                            record.get(LOAN_DETAIL.REQUESTED_QUANTITY),
                            record.get(LOAN_DETAIL.RESERVED_QUANTITY),
                            record.get(LOAN_DETAIL.DELIVERED_QUANTITY)
                    );
                    detailsByLoanId.computeIfAbsent(loanId, ignored -> new ArrayList<>()).add(item);
                    return null;
                });

        return detailsByLoanId;
    }

    private Map<UUID, LoanDetailContext> fetchLoanDetailContextByImplementUuid(Long loanId) {
        Map<UUID, LoanDetailContext> contexts = new HashMap<>();

        dsl.select(
                        LOAN_DETAIL.IMPLEMENT_ID,
                        IMPLEMENT.UUID,
                        IMPLEMENT.ITEM_TYPE,
                        LOAN_DETAIL.REQUESTED_QUANTITY,
                        LOAN_DETAIL.RESERVED_QUANTITY,
                        LOAN_DETAIL.DELIVERED_QUANTITY
                )
                .from(LOAN_DETAIL)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(LOAN_DETAIL.IMPLEMENT_ID))
                .where(LOAN_DETAIL.LOAN_ID.eq(loanId))
                .fetch(record -> {
                    UUID implementUuid = record.get(IMPLEMENT.UUID);
                    contexts.put(implementUuid, new LoanDetailContext(
                            record.get(LOAN_DETAIL.IMPLEMENT_ID),
                            implementUuid,
                            record.get(IMPLEMENT.ITEM_TYPE),
                            safe(record.get(LOAN_DETAIL.REQUESTED_QUANTITY)),
                            safe(record.get(LOAN_DETAIL.RESERVED_QUANTITY)),
                            safe(record.get(LOAN_DETAIL.DELIVERED_QUANTITY))
                    ));
                    return null;
                });

        return contexts;
    }

    private ImplementContext requireImplementContextByUuid(UUID implementUuid) {
        return dsl.select(IMPLEMENT.ID, IMPLEMENT.ITEM_TYPE)
                .from(IMPLEMENT)
                .where(IMPLEMENT.UUID.eq(implementUuid).and(IMPLEMENT.ACTIVE.isTrue()))
                .fetchOptional(record -> new ImplementContext(
                        record.get(IMPLEMENT.ID),
                        record.get(IMPLEMENT.ITEM_TYPE)
                ))
                .orElseThrow(() -> new NotFoundException(
                        "IMPLEMENT_NOT_FOUND",
                        "Implemento no encontrado"
                ));
    }

    private List<IndividualSelection> fetchAvailableIndividualsByAssetCodes(Long implementId, List<String> normalizedAssetCodes) {
        if (normalizedAssetCodes.isEmpty()) {
            return List.of();
        }

        return dsl.select(INDIVIDUAL.ID, INDIVIDUAL.UUID, INDIVIDUAL.ASSET_CODE)
                .from(INDIVIDUAL)
                .where(
                        INDIVIDUAL.IMPLEMENT_ID.eq(implementId)
                                .and(INDIVIDUAL.ACTIVE.isTrue())
                                .and(INDIVIDUAL.STATUS.eq(IndividualStatusEnum.available))
                                .and(DSL.lower(INDIVIDUAL.ASSET_CODE).in(normalizedAssetCodes))
                )
                .fetch(record -> new IndividualSelection(
                        record.get(INDIVIDUAL.ID),
                        record.get(INDIVIDUAL.UUID),
                        record.get(INDIVIDUAL.ASSET_CODE)
                ));
    }

    private List<IndividualSelection> fetchAvailableIndividuals(Long implementId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return dsl.select(INDIVIDUAL.ID, INDIVIDUAL.UUID, INDIVIDUAL.ASSET_CODE)
                .from(INDIVIDUAL)
                .where(
                        INDIVIDUAL.IMPLEMENT_ID.eq(implementId)
                                .and(INDIVIDUAL.ACTIVE.isTrue())
                                .and(INDIVIDUAL.STATUS.eq(IndividualStatusEnum.available))
                )
                .orderBy(INDIVIDUAL.ID.asc())
                .limit(limit)
                .fetch(record -> new IndividualSelection(
                        record.get(INDIVIDUAL.ID),
                        record.get(INDIVIDUAL.UUID),
                        record.get(INDIVIDUAL.ASSET_CODE)
                ));
    }

    private Map<UUID, LoanDeliveryItem> toDeliveryItemMap(List<LoanDeliveryItem> items) {
        Map<UUID, LoanDeliveryItem> mapped = new LinkedHashMap<>();
        for (LoanDeliveryItem item : items) {
            if (item == null || item.implementUuid() == null) {
                throw new BadRequestException("LOAN_DELIVERY_IMPLEMENT_REQUIRED", "Cada item de entrega requiere implement_uuid");
            }
            if (mapped.putIfAbsent(item.implementUuid(), item) != null) {
                throw new BadRequestException("LOAN_DELIVERY_DUPLICATE_IMPLEMENT", "No puedes repetir implementos en la misma entrega");
            }
        }
        return mapped;
    }

    private Map<UUID, LoanReviewItem> toReviewItemMap(List<LoanReviewItem> items) {
        Map<UUID, LoanReviewItem> mapped = new LinkedHashMap<>();
        for (LoanReviewItem item : items) {
            if (item == null || item.implementUuid() == null) {
                throw new BadRequestException("LOAN_REVIEW_IMPLEMENT_REQUIRED", "Cada item de aprobacion requiere implement_uuid");
            }
            if (item.approvedQuantity() < 0) {
                throw new BadRequestException("LOAN_REVIEW_QUANTITY_INVALID", "approved_quantity debe ser mayor o igual a cero");
            }
            if (mapped.putIfAbsent(item.implementUuid(), item) != null) {
                throw new BadRequestException("LOAN_REVIEW_DUPLICATE_IMPLEMENT", "No puedes repetir implementos al aprobar la solicitud");
            }
        }
        return mapped;
    }

    private void validateApprovalAvailability(LoanRow current, LoanDetailContext detail, int approvedQuantity) {
        if (approvedQuantity <= 0) {
            return;
        }

        Record availabilityRecord = dsl.fetchOne(
                "select * from public.fn_get_implement_availability(?::bigint, ?::timestamptz, ?::timestamptz, false, ?::bigint)",
                detail.implementId(),
                current.scheduledAt(),
                resolveExpectedReturnAt(current.scheduledAt(), current.expectedReturnAt()),
                current.loanId()
        );

        int availableQuantity = 0;
        if (availabilityRecord != null) {
            Integer rawAvailable = availabilityRecord.get("available_quantity", Integer.class);
            if (rawAvailable != null) {
                availableQuantity = rawAvailable;
            }
        }

        if (availableQuantity < approvedQuantity) {
            throw new ConflictException(
                    "LOAN_STOCK_CONFLICT",
                    "Esta solicitud excede el stock disponible de uno o mas implementos."
            );
        }
    }

    private void addAdditionalDeliveryDetails(
            Long loanId,
            Map<UUID, LoanDeliveryItem> requestedItems,
            Map<UUID, LoanDetailContext> detailByImplementUuid
    ) {
        for (LoanDeliveryItem item : requestedItems.values()) {
            if (detailByImplementUuid.containsKey(item.implementUuid())) {
                continue;
            }

            ImplementContext implement = requireImplementContextByUuid(item.implementUuid());
            int requestedQuantity = resolveDeliveryPayloadQuantity(implement.itemType(), item);

            dsl.insertInto(LOAN_DETAIL)
                    .set(LOAN_DETAIL.LOAN_ID, loanId)
                    .set(LOAN_DETAIL.IMPLEMENT_ID, implement.implementId())
                    .set(LOAN_DETAIL.REQUESTED_QUANTITY, requestedQuantity)
                    .set(LOAN_DETAIL.RESERVED_QUANTITY, 0)
                    .set(LOAN_DETAIL.DELIVERED_QUANTITY, 0)
                    .execute();

            detailByImplementUuid.put(item.implementUuid(), new LoanDetailContext(
                    implement.implementId(),
                    item.implementUuid(),
                    implement.itemType(),
                    requestedQuantity,
                    0,
                    0
            ));
        }
    }

    private int resolveDeliveryPayloadQuantity(ItemTypeEnum itemType, LoanDeliveryItem item) {
        int deliveryQuantity;
        if (itemType == ItemTypeEnum.individual && item.assetCodes() != null && !item.assetCodes().isEmpty()) {
            deliveryQuantity = normalizeAssetCodes(item.assetCodes()).size();
            if (item.quantity() != null && item.quantity() != deliveryQuantity) {
                throw new BadRequestException(
                        "LOAN_DELIVERY_ASSET_CODES_QUANTITY_MISMATCH",
                        "quantity debe coincidir con la cantidad de asset_codes seleccionados"
                );
            }
        } else {
            if (item.quantity() == null) {
                throw new BadRequestException(
                        "LOAN_DELIVERY_QUANTITY_REQUIRED",
                        "quantity es obligatorio para registrar la entrega"
                );
            }
            deliveryQuantity = item.quantity();
        }

        if (deliveryQuantity <= 0) {
            throw new BadRequestException(
                    "LOAN_DELIVERY_QUANTITY_INVALID",
                    "La cantidad a entregar debe ser mayor a cero"
            );
        }
        return deliveryQuantity;
    }

    private int resolveRequestedDeliveryQuantity(
            LoanDetailContext detailContext,
            LoanDeliveryItem requestedItem
    ) {
        if (requestedItem == null) {
            return 0;
        }

        int deliveryQuantity;
        if (detailContext.itemType() == ItemTypeEnum.individual
                && requestedItem.assetCodes() != null
                && !requestedItem.assetCodes().isEmpty()) {
            deliveryQuantity = normalizeAssetCodes(requestedItem.assetCodes()).size();
            if (requestedItem.quantity() != null && requestedItem.quantity() != deliveryQuantity) {
                throw new BadRequestException(
                        "LOAN_DELIVERY_ASSET_CODES_QUANTITY_MISMATCH",
                        "quantity debe coincidir con la cantidad de asset_codes seleccionados"
                );
            }
        } else {
            if (requestedItem.quantity() == null) {
                throw new BadRequestException(
                        "LOAN_DELIVERY_QUANTITY_REQUIRED",
                        "quantity es obligatorio para registrar la entrega"
                );
            }
            deliveryQuantity = requestedItem.quantity();
        }

        if (deliveryQuantity <= 0) {
            throw new BadRequestException(
                    "LOAN_DELIVERY_QUANTITY_INVALID",
                    "La cantidad a entregar debe ser mayor a cero"
            );
        }
        return deliveryQuantity;
    }

    private void updateLoanDetailQuantitiesForDelivery(
            Long loanId,
            LoanDetailContext detailContext,
            int deliveryQuantity
    ) {
        dsl.update(LOAN_DETAIL)
                .set(LOAN_DETAIL.RESERVED_QUANTITY, deliveryQuantity)
                .set(LOAN_DETAIL.REQUESTED_QUANTITY, Math.max(detailContext.requestedQuantity(), deliveryQuantity))
                .where(LOAN_DETAIL.LOAN_ID.eq(loanId).and(LOAN_DETAIL.IMPLEMENT_ID.eq(detailContext.implementId())))
                .execute();
    }

    private void syncPreparedReservationDifference(LoanDetailContext detailContext, int deliveryQuantity) {
        int quantityDifference = deliveryQuantity - detailContext.reservedQuantity();
        if (quantityDifference == 0) {
            return;
        }

        int updated;
        if (quantityDifference > 0) {
            updated = dsl.update(STOCK)
                    .set(STOCK.AVAILABLE, STOCK.AVAILABLE.add(-quantityDifference))
                    .set(STOCK.RESERVED, STOCK.RESERVED.add(quantityDifference))
                    .set(STOCK.UPDATED_AT, OffsetDateTime.now())
                    .where(STOCK.IMPLEMENT_ID.eq(detailContext.implementId()).and(STOCK.AVAILABLE.ge(quantityDifference)))
                    .execute();
        } else {
            int quantityToRelease = Math.abs(quantityDifference);
            updated = dsl.update(STOCK)
                    .set(STOCK.RESERVED, STOCK.RESERVED.add(-quantityToRelease))
                    .set(STOCK.AVAILABLE, STOCK.AVAILABLE.add(quantityToRelease))
                    .set(STOCK.UPDATED_AT, OffsetDateTime.now())
                    .where(STOCK.IMPLEMENT_ID.eq(detailContext.implementId()).and(STOCK.RESERVED.ge(quantityToRelease)))
                    .execute();
        }

        if (updated == 0) {
            throw new ConflictException(
                    "LOAN_STOCK_CONFLICT",
                    "No fue posible ajustar la reserva para la entrega modificada"
            );
        }
    }

    private void assignIndividualsForDelivery(
            Long loanId,
            LoanDetailContext detailContext,
            LoanDeliveryItem requestedItem,
            int deliveryQuantity
    ) {
        dsl.deleteFrom(LOAN_DETAIL_INDIVIDUAL)
                .where(
                        LOAN_DETAIL_INDIVIDUAL.LOAN_ID.eq(loanId)
                                .and(LOAN_DETAIL_INDIVIDUAL.IMPLEMENT_ID.eq(detailContext.implementId()))
                                .and(LOAN_DETAIL_INDIVIDUAL.ALLOCATION_STATUS.in(
                                        IndividualAllocationStatusEnum.reserved,
                                        IndividualAllocationStatusEnum.prepared
                                ))
                )
                .execute();

        if (deliveryQuantity == 0) {
            return;
        }
        if (requestedItem == null) {
            throw new BadRequestException(
                    "LOAN_DELIVERY_INDIVIDUAL_REQUIRED",
                    "Debes seleccionar individuales para registrar la entrega"
            );
        }

        List<IndividualSelection> selectedIndividuals;

        if (requestedItem.assetCodes() != null && !requestedItem.assetCodes().isEmpty()) {
            List<String> normalizedAssetCodes = normalizeAssetCodes(requestedItem.assetCodes());
            if (normalizedAssetCodes.size() != deliveryQuantity) {
                throw new BadRequestException(
                        "LOAN_DELIVERY_ASSET_CODES_QUANTITY_MISMATCH",
                        "La cantidad de asset_codes debe coincidir con la cantidad a entregar del implemento"
                );
            }
            selectedIndividuals = fetchAvailableIndividualsByAssetCodes(detailContext.implementId(), normalizedAssetCodes);
            if (selectedIndividuals.size() != normalizedAssetCodes.size()) {
                throw new BadRequestException(
                        "LOAN_DELIVERY_INDIVIDUAL_INVALID",
                        "Algunos asset_codes no existen, no pertenecen al implemento o no estan disponibles"
                );
            }
        } else {
            selectedIndividuals = fetchAvailableIndividuals(detailContext.implementId(), deliveryQuantity);
            if (selectedIndividuals.size() != deliveryQuantity) {
                throw new BadRequestException(
                    "LOAN_DELIVERY_INDIVIDUAL_SHORTAGE",
                    "No hay suficientes individuales disponibles para registrar la entrega"
                );
            }
        }

        int insertedLinks = 0;
        for (IndividualSelection selected : selectedIndividuals) {
            insertedLinks += dsl.insertInto(LOAN_DETAIL_INDIVIDUAL)
                    .set(LOAN_DETAIL_INDIVIDUAL.LOAN_ID, loanId)
                    .set(LOAN_DETAIL_INDIVIDUAL.IMPLEMENT_ID, detailContext.implementId())
                    .set(LOAN_DETAIL_INDIVIDUAL.INDIVIDUAL_ID, selected.individualId())
                    .set(LOAN_DETAIL_INDIVIDUAL.ALLOCATION_STATUS, IndividualAllocationStatusEnum.reserved)
                    .onConflictDoNothing()
                    .execute();
        }
        if (insertedLinks != selectedIndividuals.size()) {
            throw new BadRequestException(
                    "LOAN_DELIVERY_INDIVIDUAL_DUPLICATE",
                    "Uno o mas individuales ya estaban vinculados al prestamo"
            );
        }
    }

    private List<LoanDetailItem> insertLoanDetails(Long loanId, List<LoanRequestedItem> requestedItems) {
        List<LoanDetailItem> details = new ArrayList<>();
        for (LoanRequestedItem item : requestedItems) {
            Long implementId = requireImplementIdByUuid(item.implementUuid());

            var detail = dsl.insertInto(LOAN_DETAIL)
                    .set(LOAN_DETAIL.LOAN_ID, loanId)
                    .set(LOAN_DETAIL.IMPLEMENT_ID, implementId)
                    .set(LOAN_DETAIL.REQUESTED_QUANTITY, item.requestedQuantity())
                    .returning(LOAN_DETAIL.REQUESTED_QUANTITY, LOAN_DETAIL.RESERVED_QUANTITY, LOAN_DETAIL.DELIVERED_QUANTITY)
                    .fetchOne();

            if (detail == null) {
                throw new IllegalStateException("No fue posible crear el detalle del prestamo");
            }

            details.add(new LoanDetailItem(
                    item.implementUuid(),
                    detail.getRequestedQuantity(),
                    detail.getReservedQuantity(),
                    detail.getDeliveredQuantity()
            ));
        }
        return details;
    }

    private LoanAggregate toAggregate(LoanRow row, List<LoanDetailItem> details) {
        return new LoanAggregate(
                row.loanUuid(),
                row.requesterUuid(),
                row.roomUuid(),
                row.subjectUuid(),
                row.status(),
                row.scheduledAt(),
                row.expectedReturnAt(),
                row.createdAt(),
                details
        );
    }

    private LoanSummaryView toSummaryView(LoanSummaryRow row, List<LoanSummaryView.ItemView> items) {
        LoanSummaryView.RoomView room = row.roomUuid() == null
                ? null
                : new LoanSummaryView.RoomView(row.roomUuid(), row.roomName());
        LoanSummaryView.SubjectView subject = row.subjectUuid() == null
                ? null
                : new LoanSummaryView.SubjectView(row.subjectUuid(), row.subjectName());

        return new LoanSummaryView(
                row.loanUuid(),
                row.requesterUuid(),
                row.status(),
                row.scheduledAt(),
                row.expectedReturnAt(),
                row.createdAt(),
                room,
                subject,
                items
        );
    }

    private void callCancelLoanFunction(Long loanId, Long actorUserId, String notes) {
        try {
            dsl.fetch(
                    "select * from public.fn_cancel_loan(?::bigint, ?::bigint, ?::text)",
                    loanId,
                    actorUserId,
                    notes
            );
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_CANCEL_FAILED",
                    "No fue posible cancelar el prestamo"
            );
        }
    }

    private void callApproveLoanFunction(Long loanId, Long actorUserId, String notes) {
        try {
            dsl.fetch(
                    "select * from public.fn_approve_loan(?::bigint, ?::bigint, ?::text)",
                    loanId,
                    actorUserId,
                    notes
            );
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_REVIEW_APPROVE_FAILED",
                    "No fue posible aprobar el prestamo"
            );
        }
    }

    private void callPrepareLoanFunction(Long loanId, Long actorUserId, String notes) {
        try {
            dsl.fetch(
                    "select * from public.fn_prepare_loan(?::bigint, ?::bigint, ?::text)",
                    loanId,
                    actorUserId,
                    notes
            );
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_PREPARE_FAILED",
                    "No fue posible preparar el prestamo"
            );
        }
    }

    private void callDeliverLoanFunction(Long loanId, Long actorUserId, String notes) {
        try {
            dsl.fetch(
                    "select * from public.fn_deliver_loan(?::bigint, ?::bigint, ?::text)",
                    loanId,
                    actorUserId,
                    notes
            );
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_DELIVERY_FAILED",
                    "No fue posible entregar el prestamo"
            );
        }
    }

    private void callCompleteLoanFunction(
            Long loanId,
            Long actorUserId,
            String notes,
            List<LoanReturnPayloadItem> payloadItems
    ) {
        try {
            dsl.fetch(
                    "select * from public.fn_complete_loan(?::bigint, ?::bigint, ?::text, ?::jsonb)",
                    loanId,
                    actorUserId,
                    notes,
                    toJsonbPayload(payloadItems)
            );
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_COMPLETE_FAILED",
                    "No fue posible completar el prestamo"
            );
        }
    }

    private void callLoanStatusChangeFunction(Long loanId, Long actorUserId, LoanStatusEnum status, String notes) {
        try {
            dsl.fetch(
                    "select * from public.fn_loan_change_status(?::bigint, ?::loan_status_enum, ?::bigint, ?::text)",
                    loanId,
                    status.getLiteral(),
                    actorUserId,
                    notes
            );
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_STATUS_CHANGE_FAILED",
                    "No fue posible cambiar el estado del prestamo"
            );
        }
    }

    private int callMarkOverdueLoansFunction(Long actorUserId, OffsetDateTime currentTime) {
        try {
            Record record = dsl.fetchOne(
                    "select overdue_count from public.fn_mark_overdue_loans(?::bigint, ?::timestamptz)",
                    actorUserId,
                    currentTime
            );
            Integer overdueCount = record == null ? 0 : record.get("overdue_count", Integer.class);
            return overdueCount == null ? 0 : overdueCount;
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_MARK_OVERDUE_FAILED",
                    "No fue posible marcar prestamos overdue"
            );
        }
    }

    private int callExpirePendingLoansFunction(Long actorUserId, OffsetDateTime currentTime, int graceMinutes) {
        try {
            Record record = dsl.fetchOne(
                    "select expired_count from public.fn_expire_pending_loans(?::bigint, ?::timestamptz, ?::integer)",
                    actorUserId,
                    currentTime,
                    graceMinutes
            );
            Integer expiredCount = record == null ? 0 : record.get("expired_count", Integer.class);
            return expiredCount == null ? 0 : expiredCount;
        } catch (org.jooq.exception.DataAccessException ex) {
            throw mapLoanFunctionException(
                    ex,
                    "LOAN_EXPIRE_PENDING_FAILED",
                    "No fue posible expirar prestamos pendientes"
            );
        }
    }

    private List<LoanReturnPayloadItem> buildFullReturnAsGoodPayload(Long loanId) {
        Map<UUID, LoanDetailContext> detailByImplement = fetchLoanDetailContextByImplementUuid(loanId);
        if (detailByImplement.isEmpty()) {
            throw new BadRequestException("LOAN_COMPLETE_EMPTY", "El prestamo no tiene implementos para completar");
        }

        List<LoanReturnPayloadItem> payloadItems = new ArrayList<>();
        for (LoanDetailContext detail : detailByImplement.values()) {
            int deliveredQuantity = detail.deliveredQuantity();
            if (deliveredQuantity < 0) {
                throw new BadRequestException("LOAN_COMPLETE_INVALID_DETAIL", "El prestamo contiene cantidades invalidas");
            }
            payloadItems.add(new LoanReturnPayloadItem(
                    detail.implementId(),
                    deliveredQuantity,
                    0,
                    0,
                    0,
                    0,
                    null
            ));
        }
        return payloadItems;
    }

    private List<LoanReturnPayloadItem> buildReturnPayloadFromCommand(Long loanId, LoanReturnCommand command) {
        Map<UUID, LoanDetailContext> detailByImplement = fetchLoanDetailContextByImplementUuid(loanId);
        if (detailByImplement.isEmpty()) {
            throw new BadRequestException("LOAN_RETURN_EMPTY_DETAIL", "El prestamo no tiene implementos asociados");
        }

        List<LoanReturnIndividual> returnedIndividuals = command.returnedIndividuals() == null
                ? List.of()
                : command.returnedIndividuals();
        List<LoanReturnConsumableItem> consumableReturns = command.consumableReturns() == null
                ? List.of()
                : command.consumableReturns();

        Map<UUID, MutableReturnBreakdown> individualBreakdownByImplement = buildIndividualBreakdownByImplement(
                loanId,
                returnedIndividuals,
                detailByImplement
        );
        Map<UUID, Integer> returnedConsumableByImplement = buildConsumableReturnMap(consumableReturns, detailByImplement);

        List<LoanReturnPayloadItem> payloadItems = new ArrayList<>();
        for (LoanDetailContext detail : detailByImplement.values()) {
            int deliveredQuantity = detail.deliveredQuantity();
            if (detail.itemType() == ItemTypeEnum.individual) {
                MutableReturnBreakdown breakdown = individualBreakdownByImplement.getOrDefault(
                        detail.implementUuid(),
                        new MutableReturnBreakdown()
                );

                if (breakdown.total() == 0 && returnedIndividuals.isEmpty()) {
                    breakdown.good = deliveredQuantity;
                }

                if (breakdown.total() != deliveredQuantity) {
                    throw new BadRequestException(
                            "LOAN_RETURN_PENDING_INDIVIDUALS",
                            "Debes registrar el retorno de todos los individuales entregados"
                    );
                }

                payloadItems.add(new LoanReturnPayloadItem(
                        detail.implementId(),
                        breakdown.good,
                        breakdown.damaged,
                        breakdown.lost,
                        0,
                        breakdown.discarded,
                        null
                ));
                continue;
            }

            Integer returnedQuantity = returnedConsumableByImplement.get(detail.implementUuid());
            if (returnedQuantity == null && deliveredQuantity == 0) {
                returnedQuantity = 0;
            }
            if (returnedQuantity == null) {
                throw new BadRequestException(
                        "LOAN_RETURN_CONSUMABLE_MISSING",
                        "Debes indicar retorno para todos los implementos consumable/reusable"
                );
            }
            if (returnedQuantity < 0 || returnedQuantity > deliveredQuantity) {
                throw new BadRequestException(
                        "LOAN_RETURN_CONSUMABLE_QUANTITY_INVALID",
                        "La cantidad de retorno supera lo entregado"
                );
            }

            payloadItems.add(new LoanReturnPayloadItem(
                    detail.implementId(),
                    returnedQuantity,
                    0,
                    0,
                    deliveredQuantity - returnedQuantity,
                    0,
                    null
            ));
        }

        return payloadItems;
    }

    private Map<UUID, MutableReturnBreakdown> buildIndividualBreakdownByImplement(
            Long loanId,
            List<LoanReturnIndividual> returnedIndividuals,
            Map<UUID, LoanDetailContext> detailByImplement
    ) {
        if (returnedIndividuals.isEmpty()) {
            return Map.of();
        }

        Set<UUID> repeatedIndividuals = new HashSet<>();
        List<UUID> requestedIndividualUuids = new ArrayList<>();
        for (LoanReturnIndividual returned : returnedIndividuals) {
            if (returned == null || returned.individualUuid() == null) {
                throw new BadRequestException(
                        "LOAN_RETURN_INDIVIDUAL_REQUIRED",
                        "individual_uuid es obligatorio para retornos de implementos individuales"
                );
            }
            if (!repeatedIndividuals.add(returned.individualUuid())) {
                throw new BadRequestException("LOAN_RETURN_INDIVIDUAL_DUPLICATE", "No puedes repetir individuales en el mismo retorno");
            }
            requestedIndividualUuids.add(returned.individualUuid());
        }

        Field<UUID> individualUuidField = INDIVIDUAL.UUID.as("individual_uuid");
        Field<UUID> implementUuidField = IMPLEMENT.UUID.as("implement_uuid");
        Map<UUID, UUID> implementByIndividual = dsl.select(individualUuidField, implementUuidField)
                .from(LOAN_DETAIL_INDIVIDUAL)
                .join(INDIVIDUAL).on(INDIVIDUAL.ID.eq(LOAN_DETAIL_INDIVIDUAL.INDIVIDUAL_ID))
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(LOAN_DETAIL_INDIVIDUAL.IMPLEMENT_ID))
                .where(
                        LOAN_DETAIL_INDIVIDUAL.LOAN_ID.eq(loanId)
                                .and(individualUuidField.in(requestedIndividualUuids))
                )
                .fetchMap(individualUuidField, implementUuidField);

        if (implementByIndividual.size() != requestedIndividualUuids.size()) {
            throw new BadRequestException(
                    "LOAN_RETURN_INDIVIDUAL_NOT_FOUND",
                    "Uno o mas individuales no pertenecen a este prestamo"
            );
        }

        Map<UUID, MutableReturnBreakdown> result = new HashMap<>();
        for (LoanReturnIndividual returned : returnedIndividuals) {
            UUID implementUuid = implementByIndividual.get(returned.individualUuid());
            LoanDetailContext detail = detailByImplement.get(implementUuid);
            if (detail == null || detail.itemType() != ItemTypeEnum.individual) {
                throw new BadRequestException(
                        "LOAN_RETURN_INDIVIDUAL_NOT_FOUND",
                        "El individual no pertenece a un implemento individual del prestamo"
                );
            }

            ReturnConditionEnum condition = parseReturnCondition(returned.returnCondition());
            MutableReturnBreakdown breakdown = result.computeIfAbsent(implementUuid, ignored -> new MutableReturnBreakdown());
            breakdown.add(condition);
        }

        return result;
    }

    private Map<UUID, Integer> buildConsumableReturnMap(
            List<LoanReturnConsumableItem> consumableReturns,
            Map<UUID, LoanDetailContext> detailByImplement
    ) {
        if (consumableReturns.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Integer> returnedByImplement = new HashMap<>();
        for (LoanReturnConsumableItem consumableReturn : consumableReturns) {
            if (consumableReturn == null || consumableReturn.implementUuid() == null) {
                throw new BadRequestException(
                        "LOAN_RETURN_CONSUMABLE_IMPLEMENT_REQUIRED",
                        "implement_uuid es obligatorio para retornos de implementos consumables/reusables"
                );
            }
            if (returnedByImplement.containsKey(consumableReturn.implementUuid())) {
                throw new BadRequestException(
                        "LOAN_RETURN_CONSUMABLE_DUPLICATE",
                        "No puedes repetir implementos consumables/reusables en el mismo retorno"
                );
            }
            if (consumableReturn.quantity() == null || consumableReturn.quantity() < 0) {
                throw new BadRequestException(
                        "LOAN_RETURN_CONSUMABLE_QUANTITY_INVALID",
                        "quantity debe ser mayor o igual a cero"
                );
            }

            LoanDetailContext detail = detailByImplement.get(consumableReturn.implementUuid());
            if (detail == null) {
                throw new BadRequestException(
                        "LOAN_RETURN_CONSUMABLE_NOT_REQUESTED",
                        "El implemento consumable/reusable no forma parte del prestamo"
                );
            }
            if (detail.itemType() == ItemTypeEnum.individual) {
                throw new BadRequestException(
                        "LOAN_RETURN_CONSUMABLE_TYPE_INVALID",
                        "El implemento indicado no corresponde a un tipo consumable/reusable"
                );
            }

            returnedByImplement.put(consumableReturn.implementUuid(), consumableReturn.quantity());
        }

        return returnedByImplement;
    }

    private ReturnConditionEnum parseReturnCondition(String rawCondition) {
        ReturnConditionEnum condition = ReturnConditionEnum.lookupLiteral(
                rawCondition == null ? null : rawCondition.trim().toLowerCase(Locale.ROOT)
        );
        if (condition == null) {
            throw new BadRequestException(
                    "LOAN_RETURN_CONDITION_INVALID",
                    "return_condition debe ser good, damaged, lost o discarded"
            );
        }
        return condition;
    }

    private org.jooq.JSONB toJsonbPayload(List<LoanReturnPayloadItem> payloadItems) {
        List<Map<String, Object>> payload = payloadItems.stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("implement_id", item.implementId());
                    row.put("returned_quantity", item.returnedQuantity());
                    row.put("damaged_quantity", item.damagedQuantity());
                    row.put("lost_quantity", item.lostQuantity());
                    row.put("consumed_quantity", item.consumedQuantity());
                    row.put("discarded_quantity", item.discardedQuantity());
                    row.put("return_notes", item.returnNotes());
                    return row;
                })
                .toList();
        try {
            return org.jooq.JSONB.valueOf(objectMapper.writeValueAsString(payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible serializar el payload de cierre de prestamo", ex);
        }
    }

    private RuntimeException mapLoanFunctionException(
            org.jooq.exception.DataAccessException ex,
            String fallbackCode,
            String fallbackMessage
    ) {
        String rawMessage = extractErrorMessage(ex);
        String normalized = rawMessage.toLowerCase(Locale.ROOT);

        if (normalized.contains("loan not found")) {
            return new NotFoundException("LOAN_NOT_FOUND", rawMessage);
        }
        if (normalized.contains("actor user not found")) {
            return new NotFoundException("LOAN_ACTOR_NOT_FOUND", rawMessage);
        }
        if (normalized.contains("insufficient availability")
                || normalized.contains("insufficient physical stock")
                || normalized.contains("reserved stock is insufficient")
                || normalized.contains("stock update failed")
                || normalized.contains("stock release failed")) {
            return new ConflictException(
                    "LOAN_STOCK_CONFLICT",
                    "Esta solicitud excede el stock disponible de uno o mas implementos."
            );
        }
        if (normalized.contains("invalid")
                || normalized.contains("cannot be completed")
                || normalized.contains("cannot be cancelled")
                || normalized.contains("is not pending")
                || normalized.contains("is not approved")
                || normalized.contains("is not prepared")
                || normalized.contains("missing return payload")
                || normalized.contains("mismatch")
                || normalized.contains("negative quantities")
                || normalized.contains("not allowed")) {
            return new BadRequestException(fallbackCode, rawMessage);
        }

        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, fallbackCode, fallbackMessage);
    }

    private String extractErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = throwable.getMessage();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message == null ? "Error interno en funciones de prestamo" : message;
    }

    private Long requireUserIdByUuid(UUID userUuid) {
        Long userId = findActiveUserIdByUuid(userUuid);
        if (userId == null) {
            throw new NotFoundException("LOAN_ACTOR_NOT_FOUND", "No se pudo resolver el usuario actor");
        }
        return userId;
    }

    private Long findActiveUserIdByUuid(UUID userUuid) {
        if (userUuid == null) {
            return null;
        }
        return dsl.select(USER.ID)
                .from(USER)
                .where(USER.UUID.eq(userUuid).and(USER.ACTIVE.isTrue()))
                .fetchOne(USER.ID);
    }

    private Long findLoanIdByUuid(UUID loanUuid) {
        if (loanUuid == null) {
            return null;
        }
        return dsl.select(LOAN.ID)
                .from(LOAN)
                .where(LOAN.UUID.eq(loanUuid))
                .fetchOne(LOAN.ID);
    }

    private Long findRoomIdByUuid(UUID roomUuid) {
        if (roomUuid == null) {
            return null;
        }
        return dsl.select(ROOM.ID)
                .from(ROOM)
                .where(ROOM.UUID.eq(roomUuid).and(ROOM.ACTIVE.isTrue()))
                .fetchOne(ROOM.ID);
    }

    private Long findSubjectIdByUuid(UUID subjectUuid) {
        if (subjectUuid == null) {
            return null;
        }
        return dsl.select(SUBJECT.ID)
                .from(SUBJECT)
                .where(SUBJECT.UUID.eq(subjectUuid).and(SUBJECT.ACTIVE.isTrue()))
                .fetchOne(SUBJECT.ID);
    }

    private Long requireImplementIdByUuid(UUID implementUuid) {
        Long implementId = dsl.select(IMPLEMENT.ID)
                .from(IMPLEMENT)
                .where(
                        IMPLEMENT.UUID.eq(implementUuid)
                                .and(IMPLEMENT.ACTIVE.isTrue())
                )
                .fetchOne(IMPLEMENT.ID);

        if (implementId == null) {
            throw new IllegalStateException("No se pudo resolver implement_id para loan_detail");
        }
        return implementId;
    }

    private LoanStatus toDomainStatus(LoanStatusEnum statusEnum) {
        if (statusEnum == null) {
            return LoanStatus.PENDING;
        }
        return LoanStatus.fromLiteral(statusEnum.getLiteral()).orElse(LoanStatus.PENDING);
    }

    private LoanStatus toDomainStatusNullable(LoanStatusEnum statusEnum) {
        if (statusEnum == null) {
            return null;
        }
        return LoanStatus.fromLiteral(statusEnum.getLiteral()).orElse(null);
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private List<String> normalizeAssetCodes(List<String> assetCodes) {
        if (assetCodes == null || assetCodes.isEmpty()) {
            throw new BadRequestException("LOAN_DELIVERY_ASSET_CODES_REQUIRED", "asset_codes es obligatorio para implementos de tipo individual");
        }

        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String assetCode : assetCodes) {
            String candidate = assetCode == null ? "" : assetCode.trim().toLowerCase(Locale.ROOT);
            if (candidate.isEmpty()) {
                throw new BadRequestException("LOAN_DELIVERY_ASSET_CODE_EMPTY", "asset_codes no puede incluir valores vacios");
            }
            if (!seen.add(candidate)) {
                throw new BadRequestException("LOAN_DELIVERY_ASSET_CODE_DUPLICATE", "asset_codes no puede incluir duplicados");
            }
            normalized.add(candidate);
        }

        return normalized;
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveStatusNotes(String defaultNotes, String rawNotes) {
        String normalized = normalizeOptionalText(rawNotes);
        return normalized == null ? defaultNotes : normalized;
    }

    private OffsetDateTime resolveExpectedReturnAt(OffsetDateTime scheduledAt, OffsetDateTime expectedReturnAt) {
        if (expectedReturnAt != null) {
            return expectedReturnAt;
        }
        if (scheduledAt == null) {
            throw new BadRequestException("LOAN_EXPECTED_RETURN_REQUIRED", "expected_return_at no puede ser nulo si scheduled_at no existe");
        }
        return scheduledAt.plusHours(2);
    }

    private record LoanRow(
            Long loanId,
            UUID loanUuid,
            UUID requesterUuid,
            UUID roomUuid,
            UUID subjectUuid,
            LoanStatus status,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            OffsetDateTime createdAt
    ) {
    }

    private record LoanSummaryRow(
            Long loanId,
            UUID loanUuid,
            UUID requesterUuid,
            LoanStatus status,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            OffsetDateTime createdAt,
            UUID roomUuid,
            String roomName,
            UUID subjectUuid,
            String subjectName
    ) {
    }

    private record LoanDetailContext(
            Long implementId,
            UUID implementUuid,
            ItemTypeEnum itemType,
            int requestedQuantity,
            int reservedQuantity,
            int deliveredQuantity
    ) {
    }

    private record ImplementContext(
            Long implementId,
            ItemTypeEnum itemType
    ) {
    }

    private record IndividualSelection(
            Long individualId,
            UUID individualUuid,
            String assetCode
    ) {
    }

    private static final class MutableReturnBreakdown {
        private int good;
        private int damaged;
        private int lost;
        private int discarded;

        private void add(ReturnConditionEnum condition) {
            if (condition == ReturnConditionEnum.good) {
                good++;
            } else if (condition == ReturnConditionEnum.damaged) {
                damaged++;
            } else if (condition == ReturnConditionEnum.lost) {
                lost++;
            } else if (condition == ReturnConditionEnum.discarded) {
                discarded++;
            }
        }

        private int total() {
            return good + damaged + lost + discarded;
        }
    }

    private record LoanReturnPayloadItem(
            Long implementId,
            Integer returnedQuantity,
            Integer damagedQuantity,
            Integer lostQuantity,
            Integer consumedQuantity,
            Integer discardedQuantity,
            String returnNotes
    ) {
    }
}




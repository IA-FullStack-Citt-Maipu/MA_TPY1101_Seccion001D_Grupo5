package com.panol_project.backendpanol.modules.loan.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.Individual.INDIVIDUAL;
import static com.panol_project.backendpanol.jooq.tables.Loan.LOAN;
import static com.panol_project.backendpanol.jooq.tables.LoanDetail.LOAN_DETAIL;
import static com.panol_project.backendpanol.jooq.tables.LoanDetailIndividual.LOAN_DETAIL_INDIVIDUAL;
import static com.panol_project.backendpanol.jooq.tables.LoanStatusHistory.LOAN_STATUS_HISTORY;
import static com.panol_project.backendpanol.jooq.tables.Room.ROOM;
import static com.panol_project.backendpanol.jooq.tables.Subject.SUBJECT;
import static com.panol_project.backendpanol.jooq.tables.User.USER;

import com.panol_project.backendpanol.jooq.enums.IndividualConditionEnum;
import com.panol_project.backendpanol.jooq.enums.IndividualStatusEnum;
import com.panol_project.backendpanol.jooq.enums.ItemTypeEnum;
import com.panol_project.backendpanol.jooq.enums.LoanStatusEnum;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCreateCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanDetailItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanRequestedItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnFungibleItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnIndividual;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewDecision;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatus;
import com.panol_project.backendpanol.modules.loan.domain.LoanStockMovement;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public LoanJooqAdapter(DSLContext dsl) {
        this.dsl = dsl;
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
    public boolean existsActiveImplementByUuid(UUID implementUuid) {
        if (implementUuid == null) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(IMPLEMENT)
                        .where(
                                IMPLEMENT.UUID.eq(implementUuid)
                                        .and(IMPLEMENT.ACTIVE.isTrue())
                                        .and(IMPLEMENT.ITEM_TYPE.in(ItemTypeEnum.fungible, ItemTypeEnum.no_fungible))
                        )
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
                .set(LOAN.DUE_DATE, command.dueDate())
                .set(LOAN.CREATED_AT, now)
                .returning(LOAN.ID, LOAN.STATUS, LOAN.CREATED_AT, LOAN.UUID)
                .fetchOne();

        if (insertedLoan == null) {
            throw new IllegalStateException("No fue posible crear el prestamo");
        }

        Long loanId = insertedLoan.getId();
        List<LoanDetailItem> details = new ArrayList<>();

        for (LoanRequestedItem item : command.requestedItems()) {
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

        return new LoanAggregate(
                insertedLoan.getUuid(),
                command.requesterUuid(),
                command.roomUuid(),
                command.subjectUuid(),
                toDomainStatus(insertedLoan.getStatus()),
                command.scheduledAt(),
                command.dueDate(),
                insertedLoan.getCreatedAt(),
                details
        );
    }

    @Override
    public List<LoanAggregate> findAllVisibleLoans() {
        List<LoanRow> rows = fetchLoanRows(null);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LoanDetailItem>> detailsByLoanId = fetchLoanDetails(rows.stream().map(LoanRow::loanId).toList());
        return rows.stream()
                .map(row -> toAggregate(row, detailsByLoanId.getOrDefault(row.loanId(), List.of())))
                .toList();
    }

    @Override
    public LoanAggregate reviewLoan(LoanReviewCommand command) {
        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.PENDING) {
            throw new BadRequestException("LOAN_REVIEW_INVALID_STATE", "Solo se pueden revisar prestamos en estado pending");
        }

        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        OffsetDateTime now = OffsetDateTime.now();

        LoanStatusEnum targetStatus;
        String reviewNotes = normalizeOptionalText(command.reviewNotes());
        String rejectionReason = normalizeOptionalText(command.rejectionReason());
        OffsetDateTime approvedAt = null;

        if (command.decision() == LoanReviewDecision.APPROVE) {
            targetStatus = LoanStatusEnum.approved;
            approvedAt = now;
            rejectionReason = null;
        } else if (command.decision() == LoanReviewDecision.REJECT) {
            targetStatus = LoanStatusEnum.rejected;
            if (rejectionReason == null) {
                throw new BadRequestException("LOAN_REJECTION_REASON_REQUIRED", "rejection_reason es obligatorio al rechazar");
            }
        } else {
            throw new BadRequestException("LOAN_REVIEW_DECISION_INVALID", "decision invalida");
        }

        int updated = dsl.update(LOAN)
                .set(LOAN.STATUS, targetStatus)
                .set(LOAN.REVIEW_NOTES, reviewNotes)
                .set(LOAN.REJECTION_REASON, rejectionReason)
                .set(LOAN.APPROVED_AT, approvedAt)
                .where(LOAN.ID.eq(current.loanId()))
                .execute();

        if (updated == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOAN_REVIEW_FORBIDDEN", "No tienes permisos para revisar este prestamo");
        }

        dsl.insertInto(LOAN_STATUS_HISTORY)
                .set(LOAN_STATUS_HISTORY.LOAN_ID, current.loanId())
                .set(LOAN_STATUS_HISTORY.ACTOR_USER_ID, actorUserId)
                .set(LOAN_STATUS_HISTORY.FROM_STATUS, toJooqStatus(current.status()))
                .set(LOAN_STATUS_HISTORY.TO_STATUS, targetStatus)
                .set(LOAN_STATUS_HISTORY.NOTES, reviewNotes != null ? reviewNotes : rejectionReason)
                .set(LOAN_STATUS_HISTORY.CHANGED_AT, now)
                .execute();

        return loadLoanAggregateById(current.loanId());
    }

    @Override
    public LoanDeliveryResult deliverLoan(LoanDeliveryCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new BadRequestException("LOAN_DELIVERY_ITEMS_REQUIRED", "Debes incluir items para registrar la entrega");
        }

        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.APPROVED) {
            throw new BadRequestException("LOAN_DELIVERY_INVALID_STATE", "Solo se puede entregar un prestamo en estado approved");
        }

        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        Map<UUID, LoanDetailContext> detailByImplementUuid = fetchLoanDetailContextByImplementUuid(current.loanId());

        List<LoanStockMovement> stockMovements = new ArrayList<>();
        Set<UUID> repeatedImplementRequests = new HashSet<>();

        for (LoanDeliveryItem item : command.items()) {
            UUID implementUuid = item.implementUuid();
            if (implementUuid == null) {
                throw new BadRequestException("LOAN_DELIVERY_IMPLEMENT_REQUIRED", "Cada item de entrega requiere implement_uuid");
            }
            if (!repeatedImplementRequests.add(implementUuid)) {
                throw new BadRequestException("LOAN_DELIVERY_DUPLICATE_IMPLEMENT", "No puedes repetir implementos en la misma entrega");
            }

            LoanDetailContext detailContext = detailByImplementUuid.get(implementUuid);
            if (detailContext == null) {
                throw new BadRequestException("LOAN_DELIVERY_IMPLEMENT_NOT_REQUESTED", "El implemento no forma parte del prestamo");
            }

            int outstanding = detailContext.requestedQuantity() - detailContext.deliveredQuantity();
            if (outstanding <= 0) {
                throw new BadRequestException("LOAN_DELIVERY_ALREADY_COMPLETE", "El implemento ya fue entregado completamente");
            }

            if (detailContext.itemType() == ItemTypeEnum.no_fungible) {
                List<String> normalizedAssetCodes = normalizeAssetCodes(item.assetCodes());
                int qty = normalizedAssetCodes.size();
                if (qty > outstanding) {
                    throw new BadRequestException("LOAN_DELIVERY_QUANTITY_EXCEEDED", "La entrega supera lo solicitado para el implemento");
                }

                List<IndividualSelection> selectedIndividuals = fetchAvailableIndividualsByAssetCodes(detailContext.implementId(), normalizedAssetCodes);
                if (selectedIndividuals.size() != normalizedAssetCodes.size()) {
                    throw new BadRequestException("LOAN_DELIVERY_INDIVIDUAL_INVALID", "Algunos asset_codes no existen, no pertenecen al implemento o no estan disponibles");
                }

                int insertedLinks = 0;
                for (IndividualSelection selected : selectedIndividuals) {
                    insertedLinks += dsl.insertInto(LOAN_DETAIL_INDIVIDUAL)
                            .set(LOAN_DETAIL_INDIVIDUAL.LOAN_ID, current.loanId())
                            .set(LOAN_DETAIL_INDIVIDUAL.IMPLEMENT_ID, detailContext.implementId())
                            .set(LOAN_DETAIL_INDIVIDUAL.INDIVIDUAL_ID, selected.individualId())
                            .onConflictDoNothing()
                            .execute();
                }
                if (insertedLinks != selectedIndividuals.size()) {
                    throw new BadRequestException("LOAN_DELIVERY_INDIVIDUAL_DUPLICATE", "Uno o mas individuales ya estaban vinculados al prestamo");
                }

                incrementLoanDetailDeliveryCounters(current.loanId(), detailContext.implementId(), qty, outstanding);
                stockMovements.add(new LoanStockMovement(
                        detailContext.implementUuid(),
                        "LOAN_DELIVERY",
                        qty,
                        selectedIndividuals.stream().map(IndividualSelection::individualUuid).toList(),
                        null
                ));
            } else {
                Integer quantity = item.quantity();
                if (quantity == null || quantity <= 0) {
                    throw new BadRequestException("LOAN_DELIVERY_QUANTITY_REQUIRED", "quantity es obligatorio para implementos fungible");
                }
                if (quantity > outstanding) {
                    throw new BadRequestException("LOAN_DELIVERY_QUANTITY_EXCEEDED", "La entrega supera lo solicitado para el implemento");
                }

                incrementLoanDetailDeliveryCounters(current.loanId(), detailContext.implementId(), quantity, outstanding);
                stockMovements.add(new LoanStockMovement(
                        detailContext.implementUuid(),
                        "LOAN_DELIVERY",
                        quantity,
                        List.of(),
                        null
                ));
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        int statusUpdated = dsl.update(LOAN)
                .set(LOAN.STATUS, LoanStatusEnum.delivered)
                .set(LOAN.DELIVERED_AT, now)
                .where(LOAN.ID.eq(current.loanId()))
                .execute();

        if (statusUpdated == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOAN_DELIVERY_FORBIDDEN", "No tienes permisos para entregar este prestamo");
        }

        dsl.insertInto(LOAN_STATUS_HISTORY)
                .set(LOAN_STATUS_HISTORY.LOAN_ID, current.loanId())
                .set(LOAN_STATUS_HISTORY.ACTOR_USER_ID, actorUserId)
                .set(LOAN_STATUS_HISTORY.FROM_STATUS, toJooqStatus(current.status()))
                .set(LOAN_STATUS_HISTORY.TO_STATUS, LoanStatusEnum.delivered)
                .set(LOAN_STATUS_HISTORY.NOTES, "Entrega registrada en pañol")
                .set(LOAN_STATUS_HISTORY.CHANGED_AT, now)
                .execute();

        return new LoanDeliveryResult(loadLoanAggregateById(current.loanId()), stockMovements);
    }

    @Override
    public LoanReturnResult returnLoan(LoanReturnCommand command) {
        LoanRow current = requireLoanRowForUpdate(command.loanUuid());
        if (current.status() != LoanStatus.DELIVERED) {
            throw new BadRequestException("LOAN_RETURN_INVALID_STATE", "Solo se puede devolver un prestamo en estado delivered");
        }

        Long actorUserId = requireUserIdByUuid(command.actorUuid());
        Map<UUID, LoanDetailContext> detailByImplementUuid = fetchLoanDetailContextByImplementUuid(current.loanId());
        Map<String, GroupedReturnMovement> groupedNoFungibleReturns = new LinkedHashMap<>();
        List<LoanStockMovement> stockMovements = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        List<LoanReturnIndividual> returnedIndividuals = command.returnedIndividuals() == null ? List.of() : command.returnedIndividuals();
        Set<UUID> repeatedIndividuals = new HashSet<>();
        for (LoanReturnIndividual returned : returnedIndividuals) {
            if (returned.individualUuid() == null) {
                throw new BadRequestException("LOAN_RETURN_INDIVIDUAL_REQUIRED", "individual_uuid es obligatorio para retornos no_fungible");
            }
            if (!repeatedIndividuals.add(returned.individualUuid())) {
                throw new BadRequestException("LOAN_RETURN_INDIVIDUAL_DUPLICATE", "No puedes repetir individuales en el mismo retorno");
            }

            Long individualId = findIndividualIdByUuid(returned.individualUuid());
            if (individualId == null) {
                throw new BadRequestException("LOAN_RETURN_INDIVIDUAL_NOT_FOUND", "El individual no pertenece a este prestamo");
            }

            IndividualConditionEnum returnCondition = IndividualConditionEnum.lookupLiteral(normalizeCondition(returned.returnCondition()));
            if (returnCondition == null) {
                throw new BadRequestException("LOAN_RETURN_CONDITION_INVALID", "return_condition debe ser good, fair o poor");
            }

            var link = dsl.select(
                            LOAN_DETAIL_INDIVIDUAL.LOAN_ID,
                            LOAN_DETAIL_INDIVIDUAL.IMPLEMENT_ID,
                            LOAN_DETAIL_INDIVIDUAL.INDIVIDUAL_ID,
                            LOAN_DETAIL_INDIVIDUAL.RETURNED_AT,
                            IMPLEMENT.UUID
                    )
                    .from(LOAN_DETAIL_INDIVIDUAL)
                    .join(IMPLEMENT).on(IMPLEMENT.ID.eq(LOAN_DETAIL_INDIVIDUAL.IMPLEMENT_ID))
                    .where(
                            LOAN_DETAIL_INDIVIDUAL.LOAN_ID.eq(current.loanId())
                                    .and(LOAN_DETAIL_INDIVIDUAL.INDIVIDUAL_ID.eq(individualId))
                    )
                    .forUpdate()
                    .fetchOne();

            if (link == null) {
                throw new BadRequestException("LOAN_RETURN_INDIVIDUAL_NOT_FOUND", "El individual no pertenece a este prestamo");
            }
            if (link.get(LOAN_DETAIL_INDIVIDUAL.RETURNED_AT) != null) {
                throw new BadRequestException("LOAN_RETURN_INDIVIDUAL_ALREADY_RETURNED", "El individual ya fue registrado como devuelto");
            }

            dsl.update(LOAN_DETAIL_INDIVIDUAL)
                    .set(LOAN_DETAIL_INDIVIDUAL.RETURN_CONDITION, returnCondition)
                    .set(LOAN_DETAIL_INDIVIDUAL.RETURNED_AT, now)
                    .where(
                            LOAN_DETAIL_INDIVIDUAL.LOAN_ID.eq(current.loanId())
                                    .and(LOAN_DETAIL_INDIVIDUAL.INDIVIDUAL_ID.eq(link.get(LOAN_DETAIL_INDIVIDUAL.INDIVIDUAL_ID)))
                    )
                    .execute();

            UUID implementUuid = link.get(IMPLEMENT.UUID);
            String conditionLiteral = returnCondition.getLiteral();
            String key = implementUuid + "|" + conditionLiteral;
            GroupedReturnMovement grouped = groupedNoFungibleReturns.computeIfAbsent(
                    key,
                    ignored -> new GroupedReturnMovement(implementUuid, conditionLiteral, new ArrayList<>())
            );
            grouped.individualUuids().add(returned.individualUuid());
        }

        for (GroupedReturnMovement grouped : groupedNoFungibleReturns.values()) {
            stockMovements.add(new LoanStockMovement(
                    grouped.implementUuid(),
                    "LOAN_RETURN",
                    grouped.individualUuids().size(),
                    List.copyOf(grouped.individualUuids()),
                    grouped.condition()
            ));
        }

        List<LoanReturnFungibleItem> fungibleReturns = command.fungibleReturns() == null ? List.of() : command.fungibleReturns();
        Set<UUID> repeatedFungible = new HashSet<>();
        for (LoanReturnFungibleItem fungibleReturn : fungibleReturns) {
            if (fungibleReturn.implementUuid() == null) {
                throw new BadRequestException("LOAN_RETURN_FUNGIBLE_IMPLEMENT_REQUIRED", "implement_uuid es obligatorio para retornos fungible");
            }
            if (!repeatedFungible.add(fungibleReturn.implementUuid())) {
                throw new BadRequestException("LOAN_RETURN_FUNGIBLE_DUPLICATE", "No puedes repetir implementos fungible en el mismo retorno");
            }
            if (fungibleReturn.quantity() == null || fungibleReturn.quantity() <= 0) {
                throw new BadRequestException("LOAN_RETURN_FUNGIBLE_QUANTITY_INVALID", "quantity debe ser mayor a cero");
            }

            LoanDetailContext detail = detailByImplementUuid.get(fungibleReturn.implementUuid());
            if (detail == null) {
                throw new BadRequestException("LOAN_RETURN_FUNGIBLE_NOT_REQUESTED", "El implemento fungible no forma parte del prestamo");
            }
            if (detail.itemType() != ItemTypeEnum.fungible) {
                throw new BadRequestException("LOAN_RETURN_FUNGIBLE_TYPE_INVALID", "El implemento indicado no es de tipo fungible");
            }

            stockMovements.add(new LoanStockMovement(
                    detail.implementUuid(),
                    "LOAN_RETURN",
                    fungibleReturn.quantity(),
                    List.of(),
                    null
            ));
        }

        Integer pendingNoFungible = dsl.selectCount()
                .from(LOAN_DETAIL_INDIVIDUAL)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(LOAN_DETAIL_INDIVIDUAL.IMPLEMENT_ID))
                .where(
                        LOAN_DETAIL_INDIVIDUAL.LOAN_ID.eq(current.loanId())
                                .and(IMPLEMENT.ITEM_TYPE.eq(ItemTypeEnum.no_fungible))
                                .and(LOAN_DETAIL_INDIVIDUAL.RETURNED_AT.isNull())
                )
                .fetchOne(0, Integer.class);

        if (pendingNoFungible != null && pendingNoFungible > 0) {
            throw new BadRequestException("LOAN_RETURN_PENDING_INDIVIDUALS", "Aun existen individuales entregados sin retorno registrado");
        }

        int updated = dsl.update(LOAN)
                .set(LOAN.STATUS, LoanStatusEnum.completed)
                .set(LOAN.COMPLETED_AT, now)
                .where(LOAN.ID.eq(current.loanId()))
                .execute();

        if (updated == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOAN_RETURN_FORBIDDEN", "No tienes permisos para cerrar este prestamo");
        }

        dsl.insertInto(LOAN_STATUS_HISTORY)
                .set(LOAN_STATUS_HISTORY.LOAN_ID, current.loanId())
                .set(LOAN_STATUS_HISTORY.ACTOR_USER_ID, actorUserId)
                .set(LOAN_STATUS_HISTORY.FROM_STATUS, toJooqStatus(current.status()))
                .set(LOAN_STATUS_HISTORY.TO_STATUS, LoanStatusEnum.completed)
                .set(LOAN_STATUS_HISTORY.NOTES, "Retorno completo registrado")
                .set(LOAN_STATUS_HISTORY.CHANGED_AT, now)
                .execute();

        return new LoanReturnResult(loadLoanAggregateById(current.loanId()), stockMovements);
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
                        LOAN.DUE_DATE,
                        LOAN.CREATED_AT
                )
                .from(LOAN)
                .join(USER).on(USER.ID.eq(LOAN.REQUESTER_ID))
                .leftJoin(ROOM).on(ROOM.ID.eq(LOAN.ROOM_ID))
                .leftJoin(SUBJECT).on(SUBJECT.ID.eq(LOAN.SUBJECT_ID))
                .where(LOAN.UUID.eq(loanUuid))
                .forUpdate()
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
                record.get(LOAN.DUE_DATE),
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
                        LOAN.DUE_DATE,
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
                        record.get(LOAN.DUE_DATE),
                        record.get(LOAN.CREATED_AT)
                ));
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

    private void incrementLoanDetailDeliveryCounters(Long loanId, Long implementId, int qty, int outstanding) {
        if (qty <= 0) {
            throw new BadRequestException("LOAN_DELIVERY_QUANTITY_INVALID", "La cantidad de entrega debe ser mayor a cero");
        }
        if (qty > outstanding) {
            throw new BadRequestException("LOAN_DELIVERY_QUANTITY_EXCEEDED", "La entrega supera la cantidad pendiente");
        }

        dsl.update(LOAN_DETAIL)
                .set(LOAN_DETAIL.RESERVED_QUANTITY, LOAN_DETAIL.RESERVED_QUANTITY.add(qty))
                .set(LOAN_DETAIL.DELIVERED_QUANTITY, LOAN_DETAIL.DELIVERED_QUANTITY.add(qty))
                .where(
                        LOAN_DETAIL.LOAN_ID.eq(loanId)
                                .and(LOAN_DETAIL.IMPLEMENT_ID.eq(implementId))
                )
                .execute();
    }

    private LoanAggregate toAggregate(LoanRow row, List<LoanDetailItem> details) {
        return new LoanAggregate(
                row.loanUuid(),
                row.requesterUuid(),
                row.roomUuid(),
                row.subjectUuid(),
                row.status(),
                row.scheduledAt(),
                row.dueDate(),
                row.createdAt(),
                details
        );
    }

    private Long requireUserIdByUuid(UUID userUuid) {
        Long userId = dsl.select(USER.ID)
                .from(USER)
                .where(USER.UUID.eq(userUuid).and(USER.ACTIVE.isTrue()))
                .fetchOne(USER.ID);

        if (userId == null) {
            throw new NotFoundException("LOAN_ACTOR_NOT_FOUND", "No se pudo resolver el usuario actor");
        }
        return userId;
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
                                .and(IMPLEMENT.ITEM_TYPE.in(ItemTypeEnum.fungible, ItemTypeEnum.no_fungible))
                )
                .fetchOne(IMPLEMENT.ID);

        if (implementId == null) {
            throw new IllegalStateException("No se pudo resolver implement_id para loan_detail");
        }
        return implementId;
    }

    private Long findIndividualIdByUuid(UUID individualUuid) {
        if (individualUuid == null) {
            return null;
        }
        return dsl.select(INDIVIDUAL.ID)
                .from(INDIVIDUAL)
                .where(INDIVIDUAL.UUID.eq(individualUuid))
                .fetchOne(INDIVIDUAL.ID);
    }

    private LoanStatus toDomainStatus(LoanStatusEnum statusEnum) {
        if (statusEnum == null) {
            return LoanStatus.PENDING;
        }
        return LoanStatus.fromLiteral(statusEnum.getLiteral()).orElse(LoanStatus.PENDING);
    }

    private LoanStatusEnum toJooqStatus(LoanStatus status) {
        if (status == null) {
            return LoanStatusEnum.pending;
        }
        return LoanStatusEnum.lookupLiteral(status.literal());
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeCondition(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeAssetCodes(List<String> assetCodes) {
        if (assetCodes == null || assetCodes.isEmpty()) {
            throw new BadRequestException("LOAN_DELIVERY_ASSET_CODES_REQUIRED", "asset_codes es obligatorio para implementos no_fungible");
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

    private record LoanRow(
            Long loanId,
            UUID loanUuid,
            UUID requesterUuid,
            UUID roomUuid,
            UUID subjectUuid,
            LoanStatus status,
            OffsetDateTime scheduledAt,
            OffsetDateTime dueDate,
            OffsetDateTime createdAt
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

    private record IndividualSelection(
            Long individualId,
            UUID individualUuid,
            String assetCode
    ) {
    }

    private record GroupedReturnMovement(
            UUID implementUuid,
            String condition,
            List<UUID> individualUuids
    ) {
    }
}

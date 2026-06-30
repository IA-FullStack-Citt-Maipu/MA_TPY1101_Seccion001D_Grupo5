package com.panol_project.backendpanol.modules.catalog.stock.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Category.CATEGORY;
import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.InventoryMovement.INVENTORY_MOVEMENT;
import static com.panol_project.backendpanol.jooq.tables.Location.LOCATION;
import static com.panol_project.backendpanol.jooq.tables.Role.ROLE;
import static com.panol_project.backendpanol.jooq.tables.User.USER;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.lower;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.jooq.enums.ItemTypeEnum;
import com.panol_project.backendpanol.jooq.enums.InventoryMovementTypeEnum;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryFilter;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryItem;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryPage;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementDashboardSummary;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementRepository;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementTopImplementStat;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementTopUserStat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record8;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryMovementJooqAdapter implements InventoryMovementRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public InventoryMovementJooqAdapter(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public InventoryMovement save(InventoryMovement movement) {
        Long implementId = findImplementIdByUuid(movement.getImplementUuid());
        Long actorUserId = findUserIdByUuid(movement.getPerformedByUuid());
        if (implementId == null || actorUserId == null) {
            throw new IllegalArgumentException("No se pudo resolver implement_id/actor_user_id para inventory_movement");
        }

        OffsetDateTime createdAt = movement.getTimestamp() == null
                ? OffsetDateTime.now()
                : OffsetDateTime.ofInstant(movement.getTimestamp(), ZoneOffset.UTC);

        Map<String, Object> deltaChanges = new LinkedHashMap<>();
        deltaChanges.put("action", movement.getAction() == null ? null : movement.getAction().literal());
        deltaChanges.put("quantity", movement.getQuantity());
        deltaChanges.put("notes", movement.getNotes());

        Map<String, Object> systemicMetadata = new LinkedHashMap<>();
        systemicMetadata.put("source", "backendpanol");
        systemicMetadata.put("performed_by_uuid", movement.getPerformedByUuid() == null ? null : movement.getPerformedByUuid().toString());

        var saved = dsl.insertInto(INVENTORY_MOVEMENT)
                .set(INVENTORY_MOVEMENT.IMPLEMENT_ID, implementId)
                .set(INVENTORY_MOVEMENT.ACTOR_USER_ID, actorUserId)
                .set(
                        INVENTORY_MOVEMENT.MOVEMENT_TYPE,
                        movement.getAction() == null
                                ? InventoryMovementTypeEnum.manual_adjustment
                                : InventoryMovementTypeEnum.lookupLiteral(movement.getAction().literal())
                )
                .set(INVENTORY_MOVEMENT.QUANTITY, movement.getQuantity())
                .set(INVENTORY_MOVEMENT.DELTA_CHANGES, toJsonb(deltaChanges))
                .set(INVENTORY_MOVEMENT.SYSTEMIC_METADATA, toJsonb(systemicMetadata))
                .set(INVENTORY_MOVEMENT.CREATED_AT, createdAt)
                .returning(INVENTORY_MOVEMENT.ID, INVENTORY_MOVEMENT.CREATED_AT)
                .fetchOne();

        if (saved == null) {
            return movement;
        }

        InventoryMovement persisted = new InventoryMovement(
                movement.getImplementUuid(),
                movement.getAction(),
                movement.getQuantity(),
                movement.getPerformedByUuid(),
                saved.get(INVENTORY_MOVEMENT.CREATED_AT).toInstant(),
                movement.getNotes()
        );
        persisted.setId(String.valueOf(saved.get(INVENTORY_MOVEMENT.ID)));
        return persisted;
    }

    @Override
    public List<InventoryMovement> findTop10ByImplementUuidOrderByTimestampDesc(UUID implementUuid) {
        return dsl.select(
                        INVENTORY_MOVEMENT.ID,
                        IMPLEMENT.UUID,
                        INVENTORY_MOVEMENT.MOVEMENT_TYPE,
                        INVENTORY_MOVEMENT.QUANTITY,
                        USER.UUID,
                        INVENTORY_MOVEMENT.CREATED_AT,
                        INVENTORY_MOVEMENT.DELTA_CHANGES,
                        USER.NAME.as("performedByName")
                )
                .from(INVENTORY_MOVEMENT)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(INVENTORY_MOVEMENT.IMPLEMENT_ID))
                .leftJoin(USER).on(USER.ID.eq(INVENTORY_MOVEMENT.ACTOR_USER_ID))
                .where(IMPLEMENT.UUID.eq(implementUuid))
                .orderBy(INVENTORY_MOVEMENT.CREATED_AT.desc())
                .limit(10)
                .fetch()
                .map(this::toDomain);
    }

    @Override
    public List<InventoryMovement> findAllByOrderByTimestampDesc() {
        return findByOrderByTimestampDesc(0);
    }

    @Override
    public List<InventoryMovement> findByOrderByTimestampDesc(int limit) {
        var baseQuery = dsl.select(
                        INVENTORY_MOVEMENT.ID,
                        IMPLEMENT.UUID,
                        INVENTORY_MOVEMENT.MOVEMENT_TYPE,
                        INVENTORY_MOVEMENT.QUANTITY,
                        USER.UUID,
                        INVENTORY_MOVEMENT.CREATED_AT,
                        INVENTORY_MOVEMENT.DELTA_CHANGES,
                        USER.NAME.as("performedByName")
                )
                .from(INVENTORY_MOVEMENT)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(INVENTORY_MOVEMENT.IMPLEMENT_ID))
                .leftJoin(USER).on(USER.ID.eq(INVENTORY_MOVEMENT.ACTOR_USER_ID))
                .orderBy(INVENTORY_MOVEMENT.CREATED_AT.desc());

        if (limit > 0) {
            return baseQuery
                    .limit(limit)
                    .fetch()
                    .map(this::toDomain);
        }

        return baseQuery
                .fetch()
                .map(this::toDomain);
    }

    @Override
    public InventoryMovementHistoryPage findHistory(InventoryMovementHistoryFilter filter, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        Field<String> notesField = notesField();
        Condition condition = buildHistoryCondition(filter, notesField);

        Long totalItems = dsl.selectCount()
                .from(INVENTORY_MOVEMENT)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(INVENTORY_MOVEMENT.IMPLEMENT_ID))
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(IMPLEMENT.CATEGORY_ID))
                .leftJoin(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .leftJoin(USER).on(USER.ID.eq(INVENTORY_MOVEMENT.ACTOR_USER_ID))
                .leftJoin(ROLE).on(ROLE.ID.eq(USER.ROLE_ID))
                .where(condition)
                .fetchOne(0, Long.class);

        long resolvedTotalItems = totalItems == null ? 0 : totalItems;
        int totalPages = resolvedTotalItems == 0 ? 1 : (int) Math.ceil((double) resolvedTotalItems / safeSize);
        if (safePage > totalPages) {
            safePage = totalPages;
        }
        if (resolvedTotalItems == 0) {
            return new InventoryMovementHistoryPage(List.of(), safePage, safeSize, 0, totalPages);
        }

        int offset = (safePage - 1) * safeSize;
        List<InventoryMovementHistoryItem> items = dsl.select(
                        INVENTORY_MOVEMENT.ID,
                        INVENTORY_MOVEMENT.MOVEMENT_TYPE,
                        INVENTORY_MOVEMENT.QUANTITY,
                        INVENTORY_MOVEMENT.CREATED_AT,
                        notesField,
                        IMPLEMENT.UUID,
                        IMPLEMENT.NAME,
                        IMPLEMENT.BARCODE,
                        IMPLEMENT.ITEM_TYPE,
                        CATEGORY.NAME,
                        LOCATION.NAME,
                        USER.UUID,
                        USER.NAME,
                        ROLE.NAME
                )
                .from(INVENTORY_MOVEMENT)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(INVENTORY_MOVEMENT.IMPLEMENT_ID))
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(IMPLEMENT.CATEGORY_ID))
                .leftJoin(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .leftJoin(USER).on(USER.ID.eq(INVENTORY_MOVEMENT.ACTOR_USER_ID))
                .leftJoin(ROLE).on(ROLE.ID.eq(USER.ROLE_ID))
                .where(condition)
                .orderBy(INVENTORY_MOVEMENT.CREATED_AT.desc(), INVENTORY_MOVEMENT.ID.desc())
                .limit(safeSize)
                .offset(offset)
                .fetch(record -> new InventoryMovementHistoryItem(
                        String.valueOf(record.get(INVENTORY_MOVEMENT.ID)),
                        record.get(IMPLEMENT.UUID),
                        record.get(IMPLEMENT.NAME),
                        record.get(IMPLEMENT.BARCODE),
                        toItemTypeLiteral(record.get(IMPLEMENT.ITEM_TYPE)),
                        record.get(CATEGORY.NAME),
                        record.get(LOCATION.NAME),
                        record.get(USER.UUID),
                        record.get(USER.NAME),
                        record.get(ROLE.NAME),
                        toMovementAction(record.get(INVENTORY_MOVEMENT.MOVEMENT_TYPE)),
                        record.get(INVENTORY_MOVEMENT.QUANTITY),
                        record.get(INVENTORY_MOVEMENT.CREATED_AT) == null
                                ? Instant.now()
                                : record.get(INVENTORY_MOVEMENT.CREATED_AT).toInstant(),
                        record.get(notesField)
                ));

        return new InventoryMovementHistoryPage(items, safePage, safeSize, resolvedTotalItems, totalPages);
    }

    @Override
    public InventoryMovementDashboardSummary findDashboardSummary() {
        Field<String> performerNameField = coalesce(USER.NAME, DSL.inline("Usuario no identificado")).as("performer_name");
        Field<String> performerRoleField = coalesce(ROLE.NAME, DSL.inline("SIN_ROL")).as("performer_role");
        Field<Integer> movementCountField = DSL.count().cast(Integer.class).as("movement_count");

        int totalMovements = dsl.fetchCount(INVENTORY_MOVEMENT);

        List<InventoryMovementTopUserStat> topUsers = dsl.select(
                        performerNameField,
                        performerRoleField,
                        movementCountField
                )
                .from(INVENTORY_MOVEMENT)
                .leftJoin(USER).on(USER.ID.eq(INVENTORY_MOVEMENT.ACTOR_USER_ID))
                .leftJoin(ROLE).on(ROLE.ID.eq(USER.ROLE_ID))
                .groupBy(performerNameField, performerRoleField)
                .orderBy(movementCountField.desc(), performerNameField.asc())
                .limit(5)
                .fetch(record -> new InventoryMovementTopUserStat(
                        record.get(performerNameField),
                        record.get(performerRoleField),
                        coalesceInteger(record.get(movementCountField))
                ));

        List<InventoryMovementTopImplementStat> topImplements = dsl.select(
                        IMPLEMENT.UUID,
                        IMPLEMENT.NAME,
                        movementCountField
                )
                .from(INVENTORY_MOVEMENT)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(INVENTORY_MOVEMENT.IMPLEMENT_ID))
                .groupBy(IMPLEMENT.UUID, IMPLEMENT.NAME)
                .orderBy(movementCountField.desc(), IMPLEMENT.NAME.asc())
                .limit(6)
                .fetch(record -> new InventoryMovementTopImplementStat(
                        record.get(IMPLEMENT.UUID),
                        record.get(IMPLEMENT.NAME),
                        coalesceInteger(record.get(movementCountField))
                ));

        return new InventoryMovementDashboardSummary(totalMovements, topUsers, topImplements);
    }

    private InventoryMovement toDomain(
            Record8<Long, UUID, InventoryMovementTypeEnum, Integer, UUID, OffsetDateTime, JSONB, String> record
    ) {
        MovementAction action = record.value3() == null
                ? null
                : MovementAction.fromLiteral(record.value3().getLiteral()).orElse(null);
        String performerName = record.get("performedByName", String.class);
        InventoryMovement movement = new InventoryMovement(
                record.value2(),
                action,
                record.value4(),
                record.value5(),
                record.value6() == null ? Instant.now() : record.value6().toInstant(),
                extractNotes(record.value7())
        );
        movement.setPerformedByName(performerName);
        movement.setId(record.value1() == null ? null : String.valueOf(record.value1()));
        return movement;
    }

    private Condition buildHistoryCondition(InventoryMovementHistoryFilter filter, Field<String> notesField) {
        Condition condition = DSL.trueCondition();
        if (filter == null) {
            return condition;
        }

        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
            condition = condition.and(
                    lower(coalesce(IMPLEMENT.NAME, "")).like(pattern)
                            .or(lower(coalesce(IMPLEMENT.BARCODE, "")).like(pattern))
                            .or(lower(coalesce(USER.NAME, "")).like(pattern))
                            .or(lower(coalesce(notesField, "")).like(pattern))
            );
        }
        if (filter.action() != null) {
            condition = condition.and(INVENTORY_MOVEMENT.MOVEMENT_TYPE.eq(
                    InventoryMovementTypeEnum.lookupLiteral(filter.action().literal())
            ));
        }
        if (filter.from() != null) {
            condition = condition.and(INVENTORY_MOVEMENT.CREATED_AT.ge(filter.from()));
        }
        if (filter.to() != null) {
            condition = condition.and(INVENTORY_MOVEMENT.CREATED_AT.le(filter.to()));
        }
        return condition;
    }

    private Long findImplementIdByUuid(UUID implementUuid) {
        if (implementUuid == null) {
            return null;
        }
        return dsl.select(IMPLEMENT.ID)
                .from(IMPLEMENT)
                .where(IMPLEMENT.UUID.eq(implementUuid))
                .fetchOne(IMPLEMENT.ID);
    }

    private Long findUserIdByUuid(UUID userUuid) {
        if (userUuid == null) {
            return null;
        }
        return dsl.select(USER.ID)
                .from(USER)
                .where(USER.UUID.eq(userUuid))
                .fetchOne(USER.ID);
    }

    private JSONB toJsonb(Map<String, Object> payload) {
        try {
            return jsonb(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            return jsonb("{}");
        }
    }

    private String extractNotes(JSONB deltaChanges) {
        if (deltaChanges == null || deltaChanges.data() == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(deltaChanges.data());
            JsonNode notes = node.get("notes");
            if (notes == null || notes.isNull()) {
                return null;
            }
            return notes.asText();
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Field<String> notesField() {
        return DSL.field("{0} ->> 'notes'", String.class, INVENTORY_MOVEMENT.DELTA_CHANGES);
    }

    private MovementAction toMovementAction(InventoryMovementTypeEnum movementType) {
        return movementType == null
                ? null
                : MovementAction.fromLiteral(movementType.getLiteral()).orElse(null);
    }

    private String toItemTypeLiteral(ItemTypeEnum itemType) {
        return itemType == null ? null : itemType.getLiteral();
    }

    private int coalesceInteger(Integer value) {
        return value == null ? 0 : value;
    }
}

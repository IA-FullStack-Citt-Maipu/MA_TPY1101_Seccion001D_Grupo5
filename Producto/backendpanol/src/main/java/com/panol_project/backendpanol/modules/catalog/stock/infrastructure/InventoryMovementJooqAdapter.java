package com.panol_project.backendpanol.modules.catalog.stock.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.InventoryMovement.INVENTORY_MOVEMENT;
import static com.panol_project.backendpanol.jooq.tables.User.USER;
import static org.jooq.JSONB.jsonb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.jooq.enums.InventoryMovementTypeEnum;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementRepository;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record8;
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
                .fetch(this::toDomain);
    }

    @Override
    public List<InventoryMovement> findAllByOrderByTimestampDesc() {
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
                .orderBy(INVENTORY_MOVEMENT.CREATED_AT.desc())
                .fetch(this::toDomain);
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
}

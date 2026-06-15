package com.panol_project.backendpanol.modules.notification.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Notification.NOTIFICATION;
import static com.panol_project.backendpanol.jooq.tables.User.USER;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.modules.notification.domain.NotificationInboxItem;
import com.panol_project.backendpanol.modules.notification.domain.NotificationInboxPage;
import com.panol_project.backendpanol.modules.notification.domain.NotificationRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationJooqRepository implements NotificationRepository {

    private static final Field<String> REFERENCE_TYPE_FIELD = DSL.field(DSL.name("reference_type"), String.class);
    private static final Field<UUID> REFERENCE_ID_FIELD = DSL.field(DSL.name("reference_id"), UUID.class);
    private static final Field<JSONB> METADATA_FIELD = DSL.field(DSL.name("metadata"), JSONB.class);
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public NotificationJooqRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationInboxPage findPage(UUID userUuid, int page, int size, boolean unreadOnly) {
        int offset = (page - 1) * size;
        Condition filter = ownedBy(userUuid)
                .and(unreadOnly ? NOTIFICATION.READ_STATUS.isFalse() : DSL.trueCondition());

        List<NotificationInboxItem> items = dsl.select(
                        NOTIFICATION.UUID,
                        NOTIFICATION.TITLE,
                        NOTIFICATION.MESSAGE,
                        NOTIFICATION.READ_STATUS,
                        NOTIFICATION.CREATED_AT,
                        REFERENCE_TYPE_FIELD,
                        REFERENCE_ID_FIELD,
                        METADATA_FIELD
                )
                .from(NOTIFICATION)
                .where(filter)
                .orderBy(NOTIFICATION.CREATED_AT.desc(), NOTIFICATION.ID.desc())
                .limit(size)
                .offset(offset)
                .fetch(record -> new NotificationInboxItem(
                        record.get(NOTIFICATION.UUID),
                        record.get(NOTIFICATION.TITLE),
                        record.get(NOTIFICATION.MESSAGE),
                        Boolean.TRUE.equals(record.get(NOTIFICATION.READ_STATUS)),
                        record.get(NOTIFICATION.CREATED_AT),
                        record.get(REFERENCE_TYPE_FIELD),
                        record.get(REFERENCE_ID_FIELD),
                        parseMetadata(record.get(METADATA_FIELD))
                ));

        long totalItems = countByFilter(filter);
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        long unreadCount = countByFilter(ownedBy(userUuid).and(NOTIFICATION.READ_STATUS.isFalse()));

        return new NotificationInboxPage(items, page, size, totalItems, totalPages, unreadCount);
    }

    @Override
    public boolean markAsRead(UUID userUuid, UUID notificationUuid) {
        return dsl.update(NOTIFICATION)
                .set(NOTIFICATION.READ_STATUS, true)
                .where(NOTIFICATION.UUID.eq(notificationUuid))
                .and(ownedBy(userUuid))
                .execute() > 0;
    }

    @Override
    public int markAsRead(UUID userUuid, Collection<UUID> notificationUuids) {
        if (notificationUuids == null || notificationUuids.isEmpty()) {
            return 0;
        }

        return dsl.update(NOTIFICATION)
                .set(NOTIFICATION.READ_STATUS, true)
                .where(NOTIFICATION.UUID.in(notificationUuids))
                .and(ownedBy(userUuid))
                .and(NOTIFICATION.READ_STATUS.isFalse())
                .execute();
    }

    @Override
    public boolean markAsUnread(UUID userUuid, UUID notificationUuid) {
        return dsl.update(NOTIFICATION)
                .set(NOTIFICATION.READ_STATUS, false)
                .where(NOTIFICATION.UUID.eq(notificationUuid))
                .and(ownedBy(userUuid))
                .execute() > 0;
    }

    @Override
    public int markAsUnread(UUID userUuid, Collection<UUID> notificationUuids) {
        if (notificationUuids == null || notificationUuids.isEmpty()) {
            return 0;
        }

        return dsl.update(NOTIFICATION)
                .set(NOTIFICATION.READ_STATUS, false)
                .where(NOTIFICATION.UUID.in(notificationUuids))
                .and(ownedBy(userUuid))
                .and(NOTIFICATION.READ_STATUS.isTrue())
                .execute();
    }

    @Override
    public int markAllAsRead(UUID userUuid) {
        return dsl.update(NOTIFICATION)
                .set(NOTIFICATION.READ_STATUS, true)
                .where(ownedBy(userUuid))
                .and(NOTIFICATION.READ_STATUS.isFalse())
                .execute();
    }

    @Override
    public int deleteByUuids(UUID userUuid, Collection<UUID> notificationUuids) {
        if (notificationUuids == null || notificationUuids.isEmpty()) {
            return 0;
        }

        return dsl.deleteFrom(NOTIFICATION)
                .where(NOTIFICATION.UUID.in(notificationUuids))
                .and(ownedBy(userUuid))
                .execute();
    }

    @Override
    public int deleteReadOlderThan(OffsetDateTime cutoff, int limit) {
        if (cutoff == null || limit <= 0) {
            return 0;
        }

        return dsl.deleteFrom(NOTIFICATION)
                .where(NOTIFICATION.ID.in(
                        dsl.select(NOTIFICATION.ID)
                                .from(NOTIFICATION)
                                .where(NOTIFICATION.READ_STATUS.isTrue())
                                .and(NOTIFICATION.CREATED_AT.lt(cutoff))
                                .orderBy(NOTIFICATION.CREATED_AT.asc())
                                .limit(limit)
                ))
                .execute();
    }

    private Condition ownedBy(UUID userUuid) {
        return NOTIFICATION.USER_ID.eq(
                dsl.select(USER.ID)
                        .from(USER)
                        .where(USER.UUID.eq(userUuid))
        );
    }

    private long countByFilter(Condition filter) {
        Long count = dsl.selectCount()
                .from(NOTIFICATION)
                .where(filter)
                .fetchOne(0, Long.class);
        return count == null ? 0L : count;
    }

    private Map<String, Object> parseMetadata(JSONB metadata) {
        if (metadata == null || metadata.data() == null || metadata.data().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata.data(), METADATA_TYPE);
        } catch (IOException ex) {
            return Map.of("raw", metadata.data());
        }
    }
}

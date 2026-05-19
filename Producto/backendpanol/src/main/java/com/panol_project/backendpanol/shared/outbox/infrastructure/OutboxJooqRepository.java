package com.panol_project.backendpanol.shared.outbox.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.OutboxEvent.OUTBOX_EVENT;

import com.panol_project.backendpanol.jooq.enums.OutboxStatusEnum;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxEvent;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxEventStatus;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxJooqRepository implements OutboxRepository {

    private final DSLContext dsl;

    public OutboxJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void addPending(UUID eventId, String aggregateType, UUID aggregateId, String eventType, String payload, OffsetDateTime occurredAt) {
        dsl.insertInto(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.EVENT_ID, eventId)
                .set(OUTBOX_EVENT.AGGREGATE_TYPE, aggregateType)
                .set(OUTBOX_EVENT.AGGREGATE_ID, aggregateId)
                .set(OUTBOX_EVENT.EVENT_TYPE, eventType)
                .set(OUTBOX_EVENT.PAYLOAD, payload)
                .set(OUTBOX_EVENT.OCCURRED_AT, occurredAt)
                .set(OUTBOX_EVENT.STATUS, OutboxStatusEnum.PENDING)
                .set(OUTBOX_EVENT.RETRY_COUNT, 0)
                .execute();
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return dsl.selectFrom(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.STATUS.eq(OutboxStatusEnum.PENDING))
                .orderBy(OUTBOX_EVENT.OCCURRED_AT.asc())
                .limit(limit)
                .fetch(record -> new OutboxEvent(
                        record.getEventId(),
                        record.getAggregateType(),
                        record.getAggregateId(),
                        record.getEventType(),
                        record.getPayload(),
                        record.getOccurredAt(),
                        record.getProcessedAt(),
                        record.getRetryCount(),
                        OutboxEventStatus.valueOf(record.getStatus().name())
                ));
    }

    @Override
    public void markProcessing(UUID eventId) {
        dsl.update(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.STATUS, OutboxStatusEnum.PROCESSING)
                .where(OUTBOX_EVENT.EVENT_ID.eq(eventId))
                .execute();
    }

    @Override
    public void markSent(UUID eventId, OffsetDateTime processedAt) {
        dsl.update(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.STATUS, OutboxStatusEnum.SENT)
                .set(OUTBOX_EVENT.field("processed_at", OffsetDateTime.class), processedAt)
                .where(OUTBOX_EVENT.EVENT_ID.eq(eventId))
                .execute();
    }

    @Override
    public void markRetry(UUID eventId, int retryCount, OutboxEventStatus status) {
        dsl.update(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.RETRY_COUNT, retryCount)
                .set(OUTBOX_EVENT.STATUS, OutboxStatusEnum.valueOf(status.name()))
                .where(OUTBOX_EVENT.EVENT_ID.eq(eventId))
                .execute();
    }

    @Override
    public int countByStatus(OutboxEventStatus status) {
        Integer count = dsl.selectCount()
                .from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.STATUS.eq(OutboxStatusEnum.valueOf(status.name())))
                .fetchOne(0, Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public int countWithRetries() {
        Integer count = dsl.selectCount()
                .from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.RETRY_COUNT.gt(0))
                .fetchOne(0, Integer.class);
        return count == null ? 0 : count;
    }
}

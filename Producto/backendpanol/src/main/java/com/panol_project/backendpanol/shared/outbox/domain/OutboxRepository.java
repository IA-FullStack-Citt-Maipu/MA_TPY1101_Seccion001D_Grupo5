package com.panol_project.backendpanol.shared.outbox.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
    void addPending(UUID eventId, String aggregateType, UUID aggregateId, String eventType, String payload, OffsetDateTime occurredAt);
    List<OutboxEvent> findPending(int limit);
    void markProcessing(UUID eventId);
    void markSent(UUID eventId, OffsetDateTime processedAt);
    void markRetry(UUID eventId, int retryCount, OutboxEventStatus status);
    int countByStatus(OutboxEventStatus status);
    int countWithRetries();
}

package com.panol_project.backendpanol.shared.outbox.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        OffsetDateTime occurredAt,
        OffsetDateTime processedAt,
        Integer retryCount,
        OutboxEventStatus status
) {
}

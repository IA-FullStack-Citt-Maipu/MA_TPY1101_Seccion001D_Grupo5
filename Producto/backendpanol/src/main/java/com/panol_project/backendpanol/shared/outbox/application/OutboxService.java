package com.panol_project.backendpanol.shared.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String aggregateType, UUID aggregateId, String eventType, UUID actor, Map<String, Object> payload) {
        try {
            UUID eventId = UUID.randomUUID();
            OffsetDateTime occurredAt = OffsetDateTime.now();
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("event_id", eventId);
            envelope.put("aggregate_id", aggregateId);
            envelope.put("occurred_at", occurredAt);
            envelope.put("actor", actor);
            envelope.put("data", payload == null ? Map.of() : payload);
            String serialized = objectMapper.writeValueAsString(envelope);
            outboxRepository.addPending(
                    eventId,
                    aggregateType,
                    aggregateId,
                    eventType,
                    serialized,
                    occurredAt
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize outbox payload", ex);
        }
    }
}

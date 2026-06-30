package com.panol_project.backendpanol.shared.outbox.infrastructure;

import com.panol_project.backendpanol.shared.outbox.domain.OutboxEvent;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxPublisher;
import org.springframework.stereotype.Component;

@Component
public class PostgresOutboxPublisher implements OutboxPublisher {

    @Override
    public void publish(OutboxEvent event) {
        // PostgreSQL-only mode: dispatch is represented by the outbox status transition.
    }
}

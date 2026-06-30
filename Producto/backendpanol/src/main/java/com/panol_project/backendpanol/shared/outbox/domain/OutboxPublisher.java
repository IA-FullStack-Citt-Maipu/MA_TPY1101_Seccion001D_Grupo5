package com.panol_project.backendpanol.shared.outbox.domain;

public interface OutboxPublisher {
    void publish(OutboxEvent event);
}

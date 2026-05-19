package com.panol_project.backendpanol.shared.outbox.domain;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}

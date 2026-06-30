package com.panol_project.backendpanol.shared.outbox.application;

public record OutboxMetricsSnapshot(
        int pendingCount,
        int failedCount,
        int retryCount,
        double publishSuccessRate
) {
}

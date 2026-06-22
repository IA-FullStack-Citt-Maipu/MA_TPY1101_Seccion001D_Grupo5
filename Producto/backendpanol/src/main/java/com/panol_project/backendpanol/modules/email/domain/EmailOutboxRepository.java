package com.panol_project.backendpanol.modules.email.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository {

    List<EmailOutboxEntry> claimPending(int limit);

    void markSent(UUID eventId, OffsetDateTime processedAt, String providerMessageId);

    void markRetry(UUID eventId, int retryCount, EmailOutboxStatus status, String errorLog);
}

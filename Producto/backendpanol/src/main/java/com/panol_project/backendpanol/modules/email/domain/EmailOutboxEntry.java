package com.panol_project.backendpanol.modules.email.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record EmailOutboxEntry(
        UUID eventId,
        String recipientEmail,
        String emailType,
        Map<String, Object> templateData,
        OffsetDateTime occurredAt,
        Integer retryCount
) {
}

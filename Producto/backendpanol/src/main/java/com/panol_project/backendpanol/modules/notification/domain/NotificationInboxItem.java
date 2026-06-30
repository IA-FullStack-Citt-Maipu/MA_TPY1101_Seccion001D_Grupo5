package com.panol_project.backendpanol.modules.notification.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationInboxItem(
        UUID uuid,
        String title,
        String message,
        boolean read,
        OffsetDateTime createdAt,
        String referenceType,
        UUID referenceId,
        Map<String, Object> metadata
) {
}

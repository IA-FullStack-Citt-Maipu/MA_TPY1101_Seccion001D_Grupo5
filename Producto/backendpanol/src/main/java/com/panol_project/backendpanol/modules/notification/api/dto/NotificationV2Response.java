package com.panol_project.backendpanol.modules.notification.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationV2Response(
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

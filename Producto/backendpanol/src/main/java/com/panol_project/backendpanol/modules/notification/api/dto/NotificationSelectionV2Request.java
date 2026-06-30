package com.panol_project.backendpanol.modules.notification.api.dto;

import java.util.List;
import java.util.UUID;

public record NotificationSelectionV2Request(List<UUID> notificationUuids) {

    public NotificationSelectionV2Request {
        notificationUuids = notificationUuids == null ? List.of() : notificationUuids.stream().toList();
    }
}

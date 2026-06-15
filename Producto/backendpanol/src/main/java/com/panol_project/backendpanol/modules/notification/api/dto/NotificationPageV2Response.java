package com.panol_project.backendpanol.modules.notification.api.dto;

import java.util.List;

public record NotificationPageV2Response(
        List<NotificationV2Response> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious,
        long unreadCount
) {
}

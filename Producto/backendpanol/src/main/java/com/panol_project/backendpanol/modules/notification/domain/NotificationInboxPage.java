package com.panol_project.backendpanol.modules.notification.domain;

import java.util.List;

public record NotificationInboxPage(
        List<NotificationInboxItem> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        long unreadCount
) {
}

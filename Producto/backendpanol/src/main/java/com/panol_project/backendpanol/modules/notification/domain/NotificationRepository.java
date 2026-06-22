package com.panol_project.backendpanol.modules.notification.domain;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface NotificationRepository {

    NotificationInboxPage findPage(UUID userUuid, int page, int size, boolean unreadOnly);

    boolean markAsRead(UUID userUuid, UUID notificationUuid);

    int markAsRead(UUID userUuid, Collection<UUID> notificationUuids);

    boolean markAsUnread(UUID userUuid, UUID notificationUuid);

    int markAsUnread(UUID userUuid, Collection<UUID> notificationUuids);

    int markAllAsRead(UUID userUuid);

    int deleteByUuids(UUID userUuid, Collection<UUID> notificationUuids);

    int deleteReadOlderThan(OffsetDateTime cutoff, int limit);
}

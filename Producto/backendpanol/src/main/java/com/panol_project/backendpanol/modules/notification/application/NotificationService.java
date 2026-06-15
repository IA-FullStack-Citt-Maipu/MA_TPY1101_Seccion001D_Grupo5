package com.panol_project.backendpanol.modules.notification.application;

import com.panol_project.backendpanol.modules.notification.domain.NotificationInboxPage;
import com.panol_project.backendpanol.modules.notification.domain.NotificationRepository;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationInboxPage listar(UUID userUuid, int page, int size, boolean unreadOnly) {
        return repository.findPage(requireUserUuid(userUuid), page, size, unreadOnly);
    }

    @Transactional
    public void marcarComoLeida(UUID userUuid, UUID notificationUuid) {
        if (!repository.markAsRead(requireUserUuid(userUuid), notificationUuid)) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND", "Notificacion no encontrada");
        }
    }

    @Transactional
    public void marcarComoLeidas(UUID userUuid, List<UUID> notificationUuids) {
        if (notificationUuids == null || notificationUuids.isEmpty()) {
            return;
        }
        repository.markAsRead(requireUserUuid(userUuid), notificationUuids);
    }

    @Transactional
    public void marcarComoNoLeida(UUID userUuid, UUID notificationUuid) {
        if (!repository.markAsUnread(requireUserUuid(userUuid), notificationUuid)) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND", "Notificacion no encontrada");
        }
    }

    @Transactional
    public void marcarComoNoLeidas(UUID userUuid, List<UUID> notificationUuids) {
        if (notificationUuids == null || notificationUuids.isEmpty()) {
            return;
        }
        repository.markAsUnread(requireUserUuid(userUuid), notificationUuids);
    }

    @Transactional
    public void marcarTodasComoLeidas(UUID userUuid) {
        repository.markAllAsRead(requireUserUuid(userUuid));
    }

    @Transactional
    public void eliminar(UUID userUuid, List<UUID> notificationUuids) {
        if (notificationUuids == null || notificationUuids.isEmpty()) {
            return;
        }
        repository.deleteByUuids(requireUserUuid(userUuid), notificationUuids);
    }

    private UUID requireUserUuid(UUID userUuid) {
        if (userUuid == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida");
        }
        return userUuid;
    }
}

package com.panol_project.backendpanol.modules.notification.api;

import com.panol_project.backendpanol.modules.notification.api.dto.NotificationPageV2Response;
import com.panol_project.backendpanol.modules.notification.api.dto.NotificationSelectionV2Request;
import com.panol_project.backendpanol.modules.notification.api.dto.NotificationV2Response;
import com.panol_project.backendpanol.modules.notification.application.NotificationService;
import com.panol_project.backendpanol.modules.notification.domain.NotificationInboxItem;
import com.panol_project.backendpanol.modules.notification.domain.NotificationInboxPage;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/notifications")
public class NotificationV2Controller {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_BULK_SIZE = 100;

    private final NotificationService notificationService;
    private final CurrentUserUuidResolver currentUserUuidResolver;

    public NotificationV2Controller(
            NotificationService notificationService,
            CurrentUserUuidResolver currentUserUuidResolver
    ) {
        this.notificationService = notificationService;
        this.currentUserUuidResolver = currentUserUuidResolver;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationPageV2Response listNotifications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "false") Boolean unreadOnly,
            Authentication authentication
    ) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        validatePagination(resolvedPage, resolvedSize);

        NotificationInboxPage notificationPage = notificationService.listar(
                currentUserUuid(authentication),
                resolvedPage,
                resolvedSize,
                Boolean.TRUE.equals(unreadOnly)
        );

        return toPageResponse(notificationPage);
    }

    @PatchMapping("/{notificationUuid}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID notificationUuid, Authentication authentication) {
        notificationService.marcarComoLeida(currentUserUuid(authentication), notificationUuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markSelectionAsRead(
            @RequestBody(required = false) NotificationSelectionV2Request request,
            Authentication authentication
    ) {
        notificationService.marcarComoLeidas(currentUserUuid(authentication), sanitizeSelection(request));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{notificationUuid}/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsUnread(@PathVariable UUID notificationUuid, Authentication authentication) {
        notificationService.marcarComoNoLeida(currentUserUuid(authentication), notificationUuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markSelectionAsUnread(
            @RequestBody(required = false) NotificationSelectionV2Request request,
            Authentication authentication
    ) {
        notificationService.marcarComoNoLeidas(currentUserUuid(authentication), sanitizeSelection(request));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        notificationService.marcarTodasComoLeidas(currentUserUuid(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteSelection(
            @RequestBody(required = false) NotificationSelectionV2Request request,
            Authentication authentication
    ) {
        notificationService.eliminar(currentUserUuid(authentication), sanitizeSelection(request));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserUuid(Authentication authentication) {
        return currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));
    }

    private NotificationPageV2Response toPageResponse(NotificationInboxPage page) {
        List<NotificationV2Response> items = page.items().stream()
                .map(this::toResponse)
                .toList();
        boolean hasNext = page.page() < page.totalPages();
        boolean hasPrevious = page.page() > 1;
        return new NotificationPageV2Response(
                items,
                page.page(),
                page.size(),
                page.totalItems(),
                page.totalPages(),
                hasNext,
                hasPrevious,
                page.unreadCount()
        );
    }

    private NotificationV2Response toResponse(NotificationInboxItem item) {
        return new NotificationV2Response(
                item.uuid(),
                item.title(),
                item.message(),
                item.read(),
                item.createdAt(),
                item.referenceType(),
                item.referenceId(),
                item.metadata()
        );
    }

    private void validatePagination(int page, int size) {
        if (page < 1) {
            throw new BadRequestException("NOTIFICATION_PAGE_INVALID", "page debe ser mayor o igual a 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("NOTIFICATION_SIZE_INVALID", "size debe estar entre 1 y " + MAX_SIZE);
        }
    }

    private List<UUID> sanitizeSelection(NotificationSelectionV2Request request) {
        List<UUID> selection = request == null ? List.of() : request.notificationUuids();
        List<UUID> sanitized = selection.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (sanitized.size() > MAX_BULK_SIZE) {
            throw new BadRequestException(
                    "NOTIFICATION_BULK_SIZE_INVALID",
                    "notificationUuids no puede tener mas de " + MAX_BULK_SIZE + " elementos"
            );
        }

        return sanitized;
    }
}

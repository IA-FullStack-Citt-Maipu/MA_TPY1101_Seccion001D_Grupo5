package com.panol_project.backendpanol.modules.catalog.stock.api;

import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementHistoryItemV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementHistoryPageV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementDashboardSummaryV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementTopImplementStatV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementTopUserStatV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.RegisterMovementRequest;
import com.panol_project.backendpanol.modules.catalog.stock.application.InventoryMovementService;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementDashboardSummary;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryFilter;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryItem;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryPage;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/implements")
public class InventoryMovementV2Controller {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 15;
    private static final int MAX_SIZE = 100;
    private static final ZoneId CHILE_ZONE = ZoneId.of("America/Santiago");

    private final InventoryMovementService service;
    private final CurrentUserUuidResolver currentUserUuidResolver;

    public InventoryMovementV2Controller(
            InventoryMovementService service,
            CurrentUserUuidResolver currentUserUuidResolver
    ) {
        this.service = service;
        this.currentUserUuidResolver = currentUserUuidResolver;
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('COORDINADOR','DIRECTOR')")
    public List<InventoryMovementV2Response> listarMovimientos(@RequestParam(required = false) Integer limit) {
        List<InventoryMovement> movements = limit == null
                ? service.obtenerTodosMovimientos()
                : service.obtenerMovimientosRecientes(limit);
        return movements.stream().map(m -> new InventoryMovementV2Response(
                m.getId(),
                m.getImplementUuid(),
                m.getAction(),
                m.getQuantity(),
                resolvePerformerName(m.getPerformedByName()),
                m.getTimestamp(),
                m.getNotes()
        )).toList();
    }

    @GetMapping("/movements/summary")
    @PreAuthorize("hasAnyRole('COORDINADOR','DIRECTOR')")
    public InventoryMovementDashboardSummaryV2Response obtenerResumenMovimientos() {
        return toDashboardSummaryResponse(service.obtenerResumenDashboard());
    }

    @GetMapping("/movements/history")
    @PreAuthorize("hasAnyRole('COORDINADOR','DIRECTOR')")
    public InventoryMovementHistoryPageV2Response listarHistorialMovimientos(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        validatePagination(resolvedPage, resolvedSize);

        OffsetDateTime fromDateTime = from == null ? null : from.atStartOfDay(CHILE_ZONE).toOffsetDateTime();
        OffsetDateTime toDateTime = to == null ? null : to.plusDays(1).atStartOfDay(CHILE_ZONE).minusNanos(1).toOffsetDateTime();
        if (fromDateTime != null && toDateTime != null && fromDateTime.isAfter(toDateTime)) {
            throw new BadRequestException("MOVEMENT_HISTORY_RANGE_INVALID", "from no puede ser mayor que to");
        }

        InventoryMovementHistoryPage historyPage = service.obtenerHistorial(
                new InventoryMovementHistoryFilter(search, parseAction(action), fromDateTime, toDateTime),
                resolvedPage,
                resolvedSize
        );

        return toHistoryPageResponse(historyPage);
    }

    @PostMapping("/{implementUuid}/movements")
    @PreAuthorize("hasRole('COORDINADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementV2Response registrarMovimiento(@PathVariable UUID implementUuid, @Valid @RequestBody RegisterMovementRequest request, Authentication authentication) {
        UUID performedBy = extractUserUuid(authentication);
        MovementAction domainAction = MovementAction.fromLiteral(request.action().literal())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "STOCK_MOVEMENT_TYPE_INVALID", "movement_type invalido"));
        InventoryMovement movement = service.registrarMovimiento(
                implementUuid,
                domainAction,
                request.quantity(),
                performedBy,
                request.notes()
        );
        return new InventoryMovementV2Response(
                movement.getId(),
                movement.getImplementUuid(),
                movement.getAction(),
                movement.getQuantity(),
                "Usuario no identificado",
                movement.getTimestamp(),
                movement.getNotes()
        );
    }

    private UUID extractUserUuid(Authentication authentication) {
        return currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));
    }

    private String resolvePerformerName(String performerName) {
        if (performerName == null || performerName.isBlank()) {
            return "Usuario no identificado";
        }
        return performerName;
    }

    private InventoryMovementHistoryPageV2Response toHistoryPageResponse(InventoryMovementHistoryPage page) {
        List<InventoryMovementHistoryItemV2Response> items = page.items().stream()
                .map(this::toHistoryItemResponse)
                .toList();
        boolean hasNext = page.page() < page.totalPages();
        boolean hasPrevious = page.page() > 1;
        return new InventoryMovementHistoryPageV2Response(
                items,
                page.page(),
                page.size(),
                page.totalItems(),
                page.totalPages(),
                hasNext,
                hasPrevious
        );
    }

    private InventoryMovementHistoryItemV2Response toHistoryItemResponse(InventoryMovementHistoryItem item) {
        return new InventoryMovementHistoryItemV2Response(
                item.id(),
                item.action() == null ? null : item.action().literal(),
                item.quantity(),
                item.timestamp(),
                item.notes(),
                item.implementUuid(),
                item.implementName(),
                item.barcode(),
                item.itemType(),
                item.categoryName(),
                item.locationName(),
                item.performedByUuid(),
                resolvePerformerName(item.performedByName()),
                item.performedByRole()
        );
    }

    private InventoryMovementDashboardSummaryV2Response toDashboardSummaryResponse(InventoryMovementDashboardSummary summary) {
        return new InventoryMovementDashboardSummaryV2Response(
                summary.totalMovements(),
                summary.topUsers().stream()
                        .map(item -> new InventoryMovementTopUserStatV2Response(
                                item.name(),
                                item.role(),
                                item.movementCount()
                        ))
                        .toList(),
                summary.topImplements().stream()
                        .map(item -> new InventoryMovementTopImplementStatV2Response(
                                item.implementUuid(),
                                item.implementName(),
                                item.movementCount()
                        ))
                        .toList()
        );
    }

    private MovementAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return MovementAction.fromLiteral(action)
                .orElseThrow(() -> new BadRequestException("MOVEMENT_ACTION_INVALID", "action invalida"));
    }

    private void validatePagination(int page, int size) {
        if (page < 1) {
            throw new BadRequestException("MOVEMENT_HISTORY_PAGE_INVALID", "page debe ser mayor o igual a 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("MOVEMENT_HISTORY_SIZE_INVALID", "size debe estar entre 1 y " + MAX_SIZE);
        }
    }
}

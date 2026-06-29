package com.panol_project.backendpanol.modules.catalog.implement.api;

import com.panol_project.backendpanol.modules.catalog.implement.api.dto.CreateImplementV2Request;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.ImplementCategorySummaryV2Response;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.ImplementDetailStockResponse;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.ImplementLocationSummaryV2Response;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.ImplementRecentMovementV2Response;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.ImplementStockSummaryResponse;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.ImplementSummaryV2Response;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.ImplementV2Response;
import com.panol_project.backendpanol.modules.catalog.implement.api.dto.UpdateImplementV2Request;
import com.panol_project.backendpanol.modules.catalog.implement.application.ImplementDetailFacade;
import com.panol_project.backendpanol.modules.catalog.implement.application.ImplementService;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementSummary;
import com.panol_project.backendpanol.modules.catalog.implement.domain.Implemento;
import com.panol_project.backendpanol.modules.catalog.implement.domain.StockStatusFilter;
import com.panol_project.backendpanol.shared.error.ApiException;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/implements")
public class ImplementV2Controller {

    private static final Logger LOG = LoggerFactory.getLogger(ImplementV2Controller.class);

    private final ImplementService service;
    private final ImplementDetailFacade detailFacade;

    public ImplementV2Controller(
            ImplementService service,
            ImplementDetailFacade detailFacade
    ) {
        this.service = service;
        this.detailFacade = detailFacade;
    }

    @PostMapping
    ImplementV2Response crear(@Valid @RequestBody CreateImplementV2Request request, Authentication authentication) {
        Implemento created = service.crear(
                request.name(),
                request.description(),
                request.categoryUuid(),
                request.locationUuid(),
                request.itemType(),
                request.minStock(),
                request.barcode(),
                request.imgUrl(),
                request.observations(),
                request.costCenter(),
                request.netValue()
        );
        return buildDetailResponse(created, authentication);
    }

    @PutMapping("/{implementUuid}")
    @PreAuthorize("hasRole('COORDINADOR')")
    ImplementV2Response editar(@PathVariable UUID implementUuid, @Valid @RequestBody UpdateImplementV2Request request, Authentication authentication) {
        Implemento updated = service.editar(
                implementUuid,
                request.name(),
                request.description(),
                request.categoryUuid(),
                request.locationUuid(),
                request.itemType(),
                request.minStock(),
                request.barcode(),
                request.imgUrl(),
                request.observations(),
                request.costCenter(),
                request.netValue()
        );
        return buildDetailResponse(updated, authentication);
    }

    @GetMapping("/{implementUuid}")
    ImplementV2Response obtener(@PathVariable UUID implementUuid, Authentication authentication) {
        return buildDetailResponse(service.obtener(implementUuid), authentication);
    }

    @PatchMapping("/{implementUuid}/active")
    ImplementV2Response setActive(@PathVariable UUID implementUuid, @RequestParam boolean active, Authentication authentication) {
        Implemento updated = service.setActive(implementUuid, active);
        return buildDetailResponse(updated, authentication);
    }

    @GetMapping
    List<ImplementSummaryV2Response> listar(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) UUID categoryUuid,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(required = false) OffsetDateTime scheduledAt,
            @RequestParam(required = false) OffsetDateTime expectedReturnAt,
            @RequestParam(required = false) UUID excludeLoanUuid,
            Authentication authentication
    ) {
        boolean isCoordinador = hasRole(authentication, "ROLE_COORDINADOR");
        StockStatusFilter resolvedFilter = null;
        if (stockStatus != null) {
            if (!isCoordinador) {
                throw new AccessDeniedException("El filtro por estado de stock es exclusivo del rol Coordinador.");
            }
            resolvedFilter = StockStatusFilter.fromValue(stockStatus)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STOCK_STATUS",
                            "Valor invalido para stockStatus: " + stockStatus));
        }
        List<ImplementSummary> rows = service.listar(
                name,
                categoryUuid,
                resolvedFilter,
                scheduledAt,
                expectedReturnAt,
                excludeLoanUuid
        );
        return rows.stream().map(row -> new ImplementSummaryV2Response(
                row.uuid(),
                row.name(),
                row.description(),
                row.barcode(),
                row.individualAssetCodes(),
                row.imgUrl(),
                row.active(),
                row.stock() != null && row.stock().hasAvailability(),
                row.itemType() == null ? null : row.itemType().literal(),
                row.category() == null ? null : new ImplementCategorySummaryV2Response(
                        row.category().uuid(), row.category().name(), row.category().active()),
                row.location() == null ? null : new ImplementLocationSummaryV2Response(
                        row.location().uuid(), row.location().name(), row.location().description()),
                row.stock() == null ? null : new ImplementStockSummaryResponse(
                        row.stock().totalStock(),
                        row.stock().minStock(),
                        row.stock().available(),
                        row.stock().reserved(),
                        row.stock().loaned(),
                        row.stock().damaged()
                )
        )).toList();
    }

    private ImplementV2Response buildDetailResponse(Implemento implemento, Authentication authentication) {
        ImplementSummary summary = service.obtenerSummary(implemento.uuid());
        Integer minStock = service.obtenerStockMinimo(implemento.uuid());
        String displayLocation = service.resolveDisplayLocation(summary);

        ImplementDetailStockResponse stockResponse = null;
        if (summary.stock() != null) {
            stockResponse = new ImplementDetailStockResponse(
                    summary.stock().totalStock(),
                    summary.stock().available(),
                    summary.stock().reserved(),
                    summary.stock().loaned(),
                    summary.stock().damaged(),
                    null
            );
        }

        List<ImplementRecentMovementV2Response> movementRows = buildMovementRowsSafely(implemento.uuid());

        return new ImplementV2Response(
                implemento.uuid(),
                implemento.nombre(),
                implemento.descripcion(),
                implemento.itemType() == null ? null : implemento.itemType().literal(),
                summary.category() == null ? null : new ImplementCategorySummaryV2Response(
                        summary.category().uuid(), summary.category().name(), summary.category().active()),
                summary.location() == null ? null : new ImplementLocationSummaryV2Response(
                        summary.location().uuid(), summary.location().name(), summary.location().description()),
                displayLocation,
                implemento.categoriaUuid(),
                implemento.locationUuid(),
                minStock,
                summary.barcode(),
                summary.imgUrl(),
                implemento.observations(),
                implemento.costCenter(),
                implemento.netValue(),
                implemento.activo(),
                implemento.createdAt(),
                implemento.updatedAt(),
                stockResponse,
                movementRows
        );
    }

    private List<ImplementRecentMovementV2Response> buildMovementRowsSafely(UUID implementUuid) {
        try {
            var movements = detailFacade.getRecentMovements(implementUuid);
            return movements.stream()
                    .map(movement -> new ImplementRecentMovementV2Response(
                            movement.id(),
                            movement.implementUuid(),
                            movement.action(),
                            movement.quantity(),
                            resolvePerformerName(movement.performedByName()),
                            movement.timestamp(),
                            movement.notes()))
                    .toList();
        } catch (Exception ex) {
            LOG.warn("inventory_movements_unavailable_for_implement {}", implementUuid, ex);
            return List.of();
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private String resolvePerformerName(String performedByName) {
        if (performedByName == null || performedByName.isBlank()) {
            return "Usuario no identificado";
        }
        return performedByName;
    }
}

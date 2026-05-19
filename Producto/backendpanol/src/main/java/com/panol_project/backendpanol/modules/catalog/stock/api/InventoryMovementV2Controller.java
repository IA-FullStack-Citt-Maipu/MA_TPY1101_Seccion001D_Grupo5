package com.panol_project.backendpanol.modules.catalog.stock.api;

import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.RegisterMovementRequest;
import com.panol_project.backendpanol.modules.catalog.stock.application.InventoryMovementService;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/implements")
public class InventoryMovementV2Controller {

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
    public List<InventoryMovementV2Response> listarMovimientos() {
        List<InventoryMovement> movements = service.obtenerTodosMovimientos();
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

    @PostMapping("/{implementUuid}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementV2Response registrarMovimiento(@PathVariable UUID implementUuid, @Valid @RequestBody RegisterMovementRequest request, Authentication authentication) {
        UUID performedBy = extractUserUuid(authentication);
        MovementAction domainAction = MovementAction.valueOf(request.action().name());
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
}

package com.panol_project.backendpanol.modules.catalog.implement.application;

import com.panol_project.backendpanol.modules.catalog.stock.application.contract.InventoryMovementQueryContract;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ImplementDetailFacadeService implements ImplementDetailFacade {

    private final InventoryMovementQueryContract inventoryMovementQueryContract;

    public ImplementDetailFacadeService(InventoryMovementQueryContract inventoryMovementQueryContract) {
        this.inventoryMovementQueryContract = inventoryMovementQueryContract;
    }

    @Override
    public List<ImplementRecentMovement> getRecentMovements(UUID implementUuid) {
        return inventoryMovementQueryContract.obtenerUltimosMovimientosPorImplemento(implementUuid)
                .stream()
                .map(movement -> new ImplementRecentMovement(
                        movement.id(),
                        movement.implementUuid(),
                        movement.action(),
                        movement.quantity(),
                        movement.performedByUuid(),
                        movement.performedByName(),
                        movement.timestamp(),
                        movement.notes()
                ))
                .toList();
    }
}

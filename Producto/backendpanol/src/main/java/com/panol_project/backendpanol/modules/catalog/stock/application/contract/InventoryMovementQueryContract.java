package com.panol_project.backendpanol.modules.catalog.stock.application.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementQueryContract {

    List<InventoryMovementView> obtenerUltimosMovimientosPorImplemento(UUID implementUuid);

    record InventoryMovementView(
            String id,
            UUID implementUuid,
            String action,
            Integer quantity,
            UUID performedByUuid,
            String performedByName,
            Instant timestamp,
            String notes
    ) {
    }
}

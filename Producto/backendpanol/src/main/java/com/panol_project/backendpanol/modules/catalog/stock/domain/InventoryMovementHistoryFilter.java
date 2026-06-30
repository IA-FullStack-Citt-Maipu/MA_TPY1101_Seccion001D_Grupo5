package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.time.OffsetDateTime;

public record InventoryMovementHistoryFilter(
        String search,
        MovementAction action,
        OffsetDateTime from,
        OffsetDateTime to
) {
}

package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.time.Instant;
import java.util.UUID;

public record InventoryMovementHistoryItem(
        String id,
        UUID implementUuid,
        String implementName,
        String barcode,
        String itemType,
        String categoryName,
        String locationName,
        UUID performedByUuid,
        String performedByName,
        String performedByRole,
        MovementAction action,
        Integer quantity,
        Instant timestamp,
        String notes
) {
}

package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.UUID;

public record InventoryMovementTopImplementStat(
        UUID implementUuid,
        String implementName,
        int movementCount
) {
}

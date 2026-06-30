package com.panol_project.backendpanol.modules.catalog.stock.domain;

public record InventoryMovementTopUserStat(
        String name,
        String role,
        int movementCount
) {
}

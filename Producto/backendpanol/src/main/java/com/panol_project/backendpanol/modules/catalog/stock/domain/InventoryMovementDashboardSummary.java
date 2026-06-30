package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.List;

public record InventoryMovementDashboardSummary(
        int totalMovements,
        List<InventoryMovementTopUserStat> topUsers,
        List<InventoryMovementTopImplementStat> topImplements
) {
}

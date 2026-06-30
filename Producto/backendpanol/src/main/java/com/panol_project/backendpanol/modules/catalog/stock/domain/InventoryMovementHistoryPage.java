package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.List;

public record InventoryMovementHistoryPage(
        List<InventoryMovementHistoryItem> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}

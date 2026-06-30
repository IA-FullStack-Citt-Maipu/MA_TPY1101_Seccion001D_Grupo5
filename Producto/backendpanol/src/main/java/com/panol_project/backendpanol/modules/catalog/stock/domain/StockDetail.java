package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.List;
import java.util.UUID;

public record StockDetail(
        UUID implementUuid,
        StockItemType itemType,
        StockCounters stock,
        List<IndividualItem> individuals
) {
}

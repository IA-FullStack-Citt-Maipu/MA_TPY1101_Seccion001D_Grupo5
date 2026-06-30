package com.panol_project.backendpanol.modules.catalog.stock.domain;

public record StockCounters(
        Integer totalStock,
        Integer minStock,
        Integer available,
        Integer reserved,
        Integer loaned,
        Integer damaged
) {
}

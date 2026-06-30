package com.panol_project.backendpanol.modules.catalog.implement.domain;

public record ImplementStockSummary(
        Integer totalStock,
        Integer minStock,
        Integer available,
        Integer reserved,
        Integer loaned,
        Integer damaged
) {
    public boolean hasAvailability() {
        return available != null && available > 0;
    }
}


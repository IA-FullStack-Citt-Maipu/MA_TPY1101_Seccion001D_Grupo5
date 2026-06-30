package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImplementStockSummaryResponse(
        @JsonProperty("total_stock")
        Integer totalStock,
        @JsonProperty("min_stock")
        Integer minStock,
        Integer available,
        Integer reserved,
        Integer loaned,
        Integer damaged
) {
}


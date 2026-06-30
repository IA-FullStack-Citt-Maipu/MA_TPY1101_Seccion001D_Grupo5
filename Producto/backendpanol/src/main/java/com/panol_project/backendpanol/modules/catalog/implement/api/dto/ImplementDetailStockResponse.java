package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImplementDetailStockResponse(
        @JsonProperty("total_stock")
        Integer totalStock,
        Integer available,
        Integer reserved,
        Integer loaned,
        Integer damaged,
        @JsonProperty("available_display")
        String availableDisplay
) {
}

package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InventoryMovementHistoryPageV2Response(
        List<InventoryMovementHistoryItemV2Response> items,
        int page,
        int size,

        @JsonProperty("total_items")
        long totalItems,

        @JsonProperty("total_pages")
        int totalPages,

        @JsonProperty("has_next")
        boolean hasNext,

        @JsonProperty("has_previous")
        boolean hasPrevious
) {
}

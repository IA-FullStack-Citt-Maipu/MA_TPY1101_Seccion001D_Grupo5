package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InventoryMovementDashboardSummaryV2Response(
        @JsonProperty("total_movements")
        int totalMovements,
        @JsonProperty("top_users")
        List<InventoryMovementTopUserStatV2Response> topUsers,
        @JsonProperty("top_implements")
        List<InventoryMovementTopImplementStatV2Response> topImplements
) {
}

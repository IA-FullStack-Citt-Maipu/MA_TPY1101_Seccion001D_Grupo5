package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryMovementTopUserStatV2Response(
        String name,
        String role,
        @JsonProperty("movement_count")
        int movementCount
) {
}

package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record InventoryMovementTopImplementStatV2Response(
        @JsonProperty("implement_uuid")
        UUID implementUuid,
        @JsonProperty("implement_name")
        String implementName,
        @JsonProperty("movement_count")
        int movementCount
) {
}

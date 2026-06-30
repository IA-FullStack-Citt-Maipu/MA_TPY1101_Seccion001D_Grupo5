package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementV2Response(
        String id,
        @JsonProperty("implement_uuid")
        UUID implementUuid,
        MovementAction action,
        Integer quantity,
        @JsonProperty("performed_by")
        String performedBy,
        Instant timestamp,
        String notes
) {
}

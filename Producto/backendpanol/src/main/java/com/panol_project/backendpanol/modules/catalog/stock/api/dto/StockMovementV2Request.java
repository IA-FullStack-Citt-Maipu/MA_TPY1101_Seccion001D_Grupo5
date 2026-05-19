package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockMovementType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record StockMovementV2Request(
        @JsonProperty("movement_type")
        @NotNull(message = "El campo movement_type es obligatorio")
        StockMovementType movementType,
        Integer quantity,
        @JsonProperty("individual_uuids")
        List<UUID> individualUuids,
        String condition
) {
}

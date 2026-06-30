package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record RegisterMovementRequest(
        @JsonProperty("movement_type")
        @JsonAlias("action")
        @NotNull(message = "El campo movement_type es obligatorio")
        ManualMovementType action,
        
        @NotNull(message = "El campo quantity es obligatorio")
        Integer quantity,
        
        String notes
) {
}

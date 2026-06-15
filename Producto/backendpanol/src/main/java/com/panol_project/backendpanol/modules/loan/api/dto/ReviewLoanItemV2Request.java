package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReviewLoanItemV2Request(
        @JsonProperty("implement_uuid")
        @NotNull(message = "implement_uuid es obligatorio")
        UUID implementUuid,

        @JsonProperty("approved_quantity")
        @NotNull(message = "approved_quantity es obligatorio")
        @PositiveOrZero(message = "approved_quantity debe ser mayor o igual a cero")
        Integer approvedQuantity
) {
}

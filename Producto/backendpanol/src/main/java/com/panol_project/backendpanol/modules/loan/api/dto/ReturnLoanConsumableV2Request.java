package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReturnLoanConsumableV2Request(
        @JsonProperty("implement_uuid")
        @NotNull(message = "implement_uuid es obligatorio")
        UUID implementUuid,

        @PositiveOrZero(message = "quantity debe ser mayor o igual a cero")
        Integer quantity
) {
}

package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreateLoanItemV2Request(
        @JsonProperty("implement_uuid")
        @NotNull(message = "implement_uuid es obligatorio")
        UUID implementUuid,

        @JsonProperty("requested_quantity")
        @NotNull(message = "requested_quantity es obligatorio")
        @Positive(message = "requested_quantity debe ser mayor a cero")
        Integer requestedQuantity
) {
}

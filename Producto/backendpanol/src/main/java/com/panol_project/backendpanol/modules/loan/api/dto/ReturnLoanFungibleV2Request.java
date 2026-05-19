package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ReturnLoanFungibleV2Request(
        @JsonProperty("implement_uuid")
        @NotNull(message = "implement_uuid es obligatorio")
        UUID implementUuid,

        @Positive(message = "quantity debe ser mayor a cero")
        Integer quantity
) {
}

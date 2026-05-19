package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record LoanItemV2Response(
        @JsonProperty("implement_uuid")
        UUID implementUuid,

        @JsonProperty("requested_quantity")
        Integer requestedQuantity,

        @JsonProperty("reserved_quantity")
        Integer reservedQuantity,

        @JsonProperty("delivered_quantity")
        Integer deliveredQuantity
) {
}

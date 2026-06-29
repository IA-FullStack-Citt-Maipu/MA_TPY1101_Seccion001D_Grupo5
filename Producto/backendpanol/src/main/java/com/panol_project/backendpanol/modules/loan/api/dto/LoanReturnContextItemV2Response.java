package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record LoanReturnContextItemV2Response(
        @JsonProperty("implement_uuid")
        UUID implementUuid,

        @JsonProperty("implement_name")
        String implementName,

        @JsonProperty("item_type")
        String itemType,

        @JsonProperty("delivered_quantity")
        Integer deliveredQuantity,

        @JsonProperty("pending_return_quantity")
        Integer pendingReturnQuantity,

        @JsonProperty("individuals")
        List<LoanReturnContextIndividualV2Response> individuals
) {
}

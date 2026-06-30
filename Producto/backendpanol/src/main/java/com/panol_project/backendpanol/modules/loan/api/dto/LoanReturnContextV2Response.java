package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record LoanReturnContextV2Response(
        @JsonProperty("loan_uuid")
        UUID loanUuid,

        @JsonProperty("items")
        List<LoanReturnContextItemV2Response> items
) {
}

package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record LoanReturnContextIndividualV2Response(
        @JsonProperty("individual_uuid")
        UUID individualUuid,

        @JsonProperty("asset_code")
        String assetCode
) {
}

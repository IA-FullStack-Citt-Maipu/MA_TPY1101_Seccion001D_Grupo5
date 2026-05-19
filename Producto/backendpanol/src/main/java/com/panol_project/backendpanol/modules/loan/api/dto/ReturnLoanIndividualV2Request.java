package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReturnLoanIndividualV2Request(
        @JsonProperty("individual_uuid")
        @NotNull(message = "individual_uuid es obligatorio")
        UUID individualUuid,

        @JsonProperty("return_condition")
        @NotBlank(message = "return_condition es obligatorio")
        String returnCondition
) {
}

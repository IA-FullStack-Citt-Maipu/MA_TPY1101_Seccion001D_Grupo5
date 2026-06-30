package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReturnLoanV2Request(
        @JsonProperty("returned_individuals")
        @Valid
        List<ReturnLoanIndividualV2Request> returnedIndividuals,

        @JsonProperty("consumable_returns")
        @Valid
        List<ReturnLoanConsumableV2Request> consumableReturns,

        @Size(max = 1000, message = "notes no puede superar 1000 caracteres")
        String notes
) {
}

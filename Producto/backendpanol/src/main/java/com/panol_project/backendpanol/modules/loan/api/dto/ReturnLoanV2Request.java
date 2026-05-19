package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import java.util.List;

public record ReturnLoanV2Request(
        @JsonProperty("returned_individuals")
        @Valid
        List<ReturnLoanIndividualV2Request> returnedIndividuals,

        @JsonProperty("fungible_returns")
        @Valid
        List<ReturnLoanFungibleV2Request> fungibleReturns
) {
}

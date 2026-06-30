package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ReviewLoanV2Request(
        @NotBlank(message = "decision es obligatorio")
        String decision,

        @JsonProperty("notes")
        String notes,

        @JsonProperty("items")
        @Valid
        List<ReviewLoanItemV2Request> items
) {
}

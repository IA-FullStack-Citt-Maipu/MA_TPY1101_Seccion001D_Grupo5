package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ReviewLoanV2Request(
        @NotBlank(message = "decision es obligatorio")
        String decision,

        @JsonProperty("notes")
        String notes
) {
}

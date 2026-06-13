package com.panol_project.backendpanol.modules.loan.api.dto;

import jakarta.validation.constraints.Size;

public record CompleteLoanV2Request(
        @Size(max = 1000, message = "notes no puede superar 1000 caracteres")
        String notes
) {
}

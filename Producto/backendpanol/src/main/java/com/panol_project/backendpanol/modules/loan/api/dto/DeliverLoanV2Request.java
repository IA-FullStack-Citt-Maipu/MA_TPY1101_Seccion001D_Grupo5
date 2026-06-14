package com.panol_project.backendpanol.modules.loan.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeliverLoanV2Request(
        @NotEmpty(message = "Debes incluir al menos un item de entrega")
        @Valid
        List<DeliverLoanItemV2Request> items,

        @Size(max = 1000, message = "notes no puede superar 1000 caracteres")
        String notes
) {
}

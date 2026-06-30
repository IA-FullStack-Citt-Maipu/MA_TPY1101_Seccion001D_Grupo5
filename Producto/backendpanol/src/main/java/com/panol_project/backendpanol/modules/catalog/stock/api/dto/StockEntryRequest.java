package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record StockEntryRequest(
        @NotNull(message = "quantity es obligatorio")
        @Positive(message = "quantity debe ser un entero positivo")
        Integer quantity,
        @JsonProperty("asset_codes")
        List<String> assetCodes,
        String status,
        String condition,
        String notes,
        @JsonProperty("current_location_uuid")
        UUID currentLocationUuid,
        @JsonProperty("remaining_life")
        Integer remainingLife,
        @JsonProperty("asset_code_reprint_required")
        Boolean assetCodeReprintRequired,
        @JsonProperty("individual_entries")
        List<StockEntryIndividualRequest> individualEntries
) {
}

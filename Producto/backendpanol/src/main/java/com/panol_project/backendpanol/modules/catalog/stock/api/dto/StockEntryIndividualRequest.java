package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record StockEntryIndividualRequest(
        @JsonProperty("asset_code")
        String assetCode,
        String status,
        String condition,
        @JsonProperty("current_location_uuid")
        UUID currentLocationUuid,
        @JsonProperty("remaining_life")
        Integer remainingLife,
        @JsonProperty("asset_code_reprint_required")
        Boolean assetCodeReprintRequired
) {
}

package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record IndividualV2Response(
        UUID uuid,
        @JsonProperty("asset_code")
        String assetCode,
        String status,
        String condition,
        String notes,
        @JsonProperty("current_location_uuid")
        UUID currentLocationUuid,
        Boolean active
) {
}

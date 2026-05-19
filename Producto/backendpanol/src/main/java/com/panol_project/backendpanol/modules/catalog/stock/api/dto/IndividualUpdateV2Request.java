package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record IndividualUpdateV2Request(
        String status,
        String condition,
        String notes,
        @JsonProperty("current_location_uuid")
        UUID currentLocationUuid,
        Boolean active
) {
}

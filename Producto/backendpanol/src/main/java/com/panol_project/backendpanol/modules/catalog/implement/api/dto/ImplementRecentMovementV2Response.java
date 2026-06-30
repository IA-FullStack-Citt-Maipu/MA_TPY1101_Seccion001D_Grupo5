package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record ImplementRecentMovementV2Response(
        String id,
        @JsonProperty("implement_uuid")
        UUID implementUuid,
        String action,
        Integer quantity,
        @JsonProperty("performed_by")
        String performedBy,
        Instant timestamp,
        String notes
) {
}

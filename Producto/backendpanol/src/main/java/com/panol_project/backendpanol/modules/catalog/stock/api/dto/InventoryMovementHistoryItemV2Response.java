package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementHistoryItemV2Response(
        String id,
        String action,
        Integer quantity,
        Instant timestamp,
        String notes,

        @JsonProperty("implement_uuid")
        UUID implementUuid,

        @JsonProperty("implement_name")
        String implementName,

        String barcode,

        @JsonProperty("item_type")
        String itemType,

        @JsonProperty("category_name")
        String categoryName,

        @JsonProperty("location_name")
        String locationName,

        @JsonProperty("performed_by_uuid")
        UUID performedByUuid,

        @JsonProperty("performed_by")
        String performedBy,

        @JsonProperty("performed_by_role")
        String performedByRole
) {
}

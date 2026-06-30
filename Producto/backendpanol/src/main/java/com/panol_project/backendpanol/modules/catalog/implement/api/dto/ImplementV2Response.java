package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ImplementV2Response(
        UUID uuid,
        String name,
        String description,
        @JsonProperty("item_type")
        String itemType,
        ImplementCategorySummaryV2Response category,
        ImplementLocationSummaryV2Response location,
        @JsonProperty("display_location")
        String displayLocation,
        @JsonProperty("category_uuid")
        UUID categoryUuid,
        @JsonProperty("location_uuid")
        UUID locationUuid,
        @JsonProperty("min_stock")
        Integer minStock,
        String barcode,
        @JsonProperty("img_url")
        String imgUrl,
        String observations,
        @JsonProperty("cost_center")
        String costCenter,
        @JsonProperty("net_value")
        BigDecimal netValue,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ImplementDetailStockResponse stock,
        @JsonProperty("recent_movements")
        List<ImplementRecentMovementV2Response> recentMovements
) {
}

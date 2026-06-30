package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record ImplementSummaryV2Response(
        UUID uuid,
        String name,
        String description,
        String barcode,
        @JsonProperty("individual_asset_codes")
        List<String> individualAssetCodes,
        String imgUrl,
        Boolean active,
        Boolean available,
        @JsonProperty("item_type")
        String itemType,
        ImplementCategorySummaryV2Response category,
        ImplementLocationSummaryV2Response location,
        ImplementStockSummaryResponse stock
) {
}

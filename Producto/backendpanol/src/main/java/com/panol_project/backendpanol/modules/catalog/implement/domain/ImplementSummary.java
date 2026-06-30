package com.panol_project.backendpanol.modules.catalog.implement.domain;

import java.util.List;
import java.util.UUID;

public record ImplementSummary(
        UUID uuid,
        String name,
        String description,
        String barcode,
        List<String> individualAssetCodes,
        String imgUrl,
        Boolean active,
        ImplementItemType itemType,
        ImplementCategorySummary category,
        ImplementLocationSummary location,
        ImplementStockSummary stock
) {
}

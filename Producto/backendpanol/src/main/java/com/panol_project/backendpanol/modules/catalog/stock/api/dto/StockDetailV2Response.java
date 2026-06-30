package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record StockDetailV2Response(
        @JsonProperty("implement_uuid")
        UUID implementUuid,
        @JsonProperty("item_type")
        String itemType,
        StockCountersResponse stock,
        List<IndividualV2Response> individuals
) {
}

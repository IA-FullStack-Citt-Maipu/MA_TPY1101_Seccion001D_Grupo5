package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LoanRequesterHistoryPageV2Response(
        LoanRequesterItemV2Response requester,
        List<LoanRequesterHistoryItemV2Response> items,
        int page,
        int size,
        @JsonProperty("total_items")
        long totalItems,
        @JsonProperty("total_pages")
        int totalPages,
        @JsonProperty("has_next")
        boolean hasNext,
        @JsonProperty("has_previous")
        boolean hasPrevious
) {
}

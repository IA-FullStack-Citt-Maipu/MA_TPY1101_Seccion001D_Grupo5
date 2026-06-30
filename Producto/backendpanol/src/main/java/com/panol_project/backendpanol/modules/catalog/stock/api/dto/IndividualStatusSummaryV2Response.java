package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

public record IndividualStatusSummaryV2Response(
        int available,
        int loaned,
        int maintenance,
        int damaged,
        int blocked,
        int retired,
        int total
) {
}

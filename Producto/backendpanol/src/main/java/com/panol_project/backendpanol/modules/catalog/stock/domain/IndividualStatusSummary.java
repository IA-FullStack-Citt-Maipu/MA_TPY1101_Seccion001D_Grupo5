package com.panol_project.backendpanol.modules.catalog.stock.domain;

public record IndividualStatusSummary(
        int available,
        int loaned,
        int maintenance,
        int damaged,
        int blocked,
        int retired,
        int total
) {
}

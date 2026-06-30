package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.UUID;

public record LoanReturnContextView(
        UUID loanUuid,
        List<ItemView> items
) {
    public record ItemView(
            UUID implementUuid,
            String implementName,
            String itemType,
            Integer deliveredQuantity,
            Integer pendingReturnQuantity,
            List<IndividualView> individuals
    ) {
    }

    public record IndividualView(
            UUID individualUuid,
            String assetCode
    ) {
    }
}

package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanRequestedItemAvailability(
        UUID implementUuid,
        String implementName,
        boolean active,
        int availableQuantity
) {
}

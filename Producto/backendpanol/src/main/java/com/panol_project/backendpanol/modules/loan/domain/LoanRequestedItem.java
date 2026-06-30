package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanRequestedItem(
        UUID implementUuid,
        Integer requestedQuantity
) {
}

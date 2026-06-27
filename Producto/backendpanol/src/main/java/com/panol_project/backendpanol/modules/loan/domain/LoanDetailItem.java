package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanDetailItem(
        UUID implementUuid,
        Integer requestedQuantity,
        Integer reservedQuantity,
        Integer deliveredQuantity,
        Integer returnedQuantity
) {
    public LoanDetailItem(
            UUID implementUuid,
            Integer requestedQuantity,
            Integer reservedQuantity,
            Integer deliveredQuantity
    ) {
        this(implementUuid, requestedQuantity, reservedQuantity, deliveredQuantity, 0);
    }
}

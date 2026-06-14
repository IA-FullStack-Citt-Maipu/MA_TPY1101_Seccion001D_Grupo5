package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanReviewItem(
        UUID implementUuid,
        int approvedQuantity
) {
}

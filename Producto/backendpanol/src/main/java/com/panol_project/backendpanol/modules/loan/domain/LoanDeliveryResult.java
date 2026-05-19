package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;

public record LoanDeliveryResult(
        LoanAggregate loan,
        List<LoanStockMovement> stockMovements
) {
}

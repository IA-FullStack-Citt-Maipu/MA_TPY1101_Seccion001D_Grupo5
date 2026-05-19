package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.UUID;

public record LoanDeliveryCommand(
        UUID loanUuid,
        UUID actorUuid,
        List<LoanDeliveryItem> items
) {
}

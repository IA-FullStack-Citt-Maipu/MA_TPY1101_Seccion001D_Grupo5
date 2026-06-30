package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.UUID;

public record LoanReturnCommand(
        UUID loanUuid,
        UUID actorUuid,
        String notes,
        List<LoanReturnIndividual> returnedIndividuals,
        List<LoanReturnConsumableItem> consumableReturns
) {
}

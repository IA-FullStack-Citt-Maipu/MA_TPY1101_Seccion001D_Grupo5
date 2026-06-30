package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanPrepareCommand(
        UUID loanUuid,
        UUID actorUuid,
        String notes
) {
}

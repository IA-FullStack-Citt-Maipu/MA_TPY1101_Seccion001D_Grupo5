package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanCompleteCommand(
        UUID loanUuid,
        UUID actorUuid,
        String notes
) {
}

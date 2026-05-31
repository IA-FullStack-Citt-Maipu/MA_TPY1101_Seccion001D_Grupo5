package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanCancelCommand(
        UUID loanUuid,
        UUID actorUuid,
        String notes
) {
}

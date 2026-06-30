package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanReturnIndividual(
        UUID individualUuid,
        String returnCondition
) {
}

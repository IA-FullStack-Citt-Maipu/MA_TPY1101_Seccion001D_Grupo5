package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanImplementAvailability(
        UUID uuid,
        boolean active
) {
}

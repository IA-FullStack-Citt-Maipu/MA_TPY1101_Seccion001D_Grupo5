package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.UUID;

public record PrepararPrestamoCommand(
        UUID loanUuid,
        UUID actorUuid,
        String notes
) {
}

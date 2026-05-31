package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.UUID;

public record CancelarPrestamoCommand(
        UUID loanUuid,
        UUID actorUuid,
        String notes
) {
}

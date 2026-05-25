package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.UUID;

public record CompletarPrestamoCommand(
        UUID loanUuid,
        UUID actorUuid
) {
}

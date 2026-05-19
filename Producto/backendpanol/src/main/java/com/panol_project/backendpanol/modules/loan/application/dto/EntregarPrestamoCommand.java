package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.List;
import java.util.UUID;

public record EntregarPrestamoCommand(
        UUID loanUuid,
        UUID actorUuid,
        List<EntregarPrestamoItemCommand> items
) {
}

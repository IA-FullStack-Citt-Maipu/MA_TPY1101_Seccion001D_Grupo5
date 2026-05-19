package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.List;
import java.util.UUID;

public record DevolverPrestamoCommand(
        UUID loanUuid,
        UUID actorUuid,
        List<DevolverPrestamoIndividualCommand> returnedIndividuals,
        List<DevolverPrestamoFungibleCommand> fungibleReturns
) {
}

package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.UUID;

public record DevolverPrestamoIndividualCommand(
        UUID individualUuid,
        String returnCondition
) {
}

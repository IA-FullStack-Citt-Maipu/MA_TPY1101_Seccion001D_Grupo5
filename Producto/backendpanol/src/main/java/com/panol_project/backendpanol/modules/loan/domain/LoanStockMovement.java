package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.UUID;

public record LoanStockMovement(
        UUID implementUuid,
        String movementType,
        Integer quantity,
        List<UUID> individualUuids,
        String condition
) {
}

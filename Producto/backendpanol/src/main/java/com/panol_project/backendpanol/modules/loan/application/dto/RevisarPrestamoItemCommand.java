package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.UUID;

public record RevisarPrestamoItemCommand(
        UUID implementUuid,
        Integer approvedQuantity
) {
}

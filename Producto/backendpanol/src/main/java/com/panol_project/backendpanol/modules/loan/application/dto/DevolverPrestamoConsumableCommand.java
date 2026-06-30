package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.UUID;

public record DevolverPrestamoConsumableCommand(
        UUID implementUuid,
        Integer quantity
) {
}

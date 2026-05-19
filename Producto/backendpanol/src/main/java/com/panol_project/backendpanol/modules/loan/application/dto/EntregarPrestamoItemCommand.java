package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.List;
import java.util.UUID;

public record EntregarPrestamoItemCommand(
        UUID implementUuid,
        Integer quantity,
        List<String> assetCodes
) {
}

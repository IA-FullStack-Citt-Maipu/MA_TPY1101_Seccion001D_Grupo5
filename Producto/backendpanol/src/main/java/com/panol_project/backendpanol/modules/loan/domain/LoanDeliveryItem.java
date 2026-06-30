package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.UUID;

public record LoanDeliveryItem(
        UUID implementUuid,
        Integer quantity,
        List<String> assetCodes
) {
}

package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanReturnConsumableItem(
        UUID implementUuid,
        Integer quantity
) {
}

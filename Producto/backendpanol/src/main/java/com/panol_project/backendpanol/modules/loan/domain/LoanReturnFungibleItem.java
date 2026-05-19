package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanReturnFungibleItem(
        UUID implementUuid,
        Integer quantity
) {
}

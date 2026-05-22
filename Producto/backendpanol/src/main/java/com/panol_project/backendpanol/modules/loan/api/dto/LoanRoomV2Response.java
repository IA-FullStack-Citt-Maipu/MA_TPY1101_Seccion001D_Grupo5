package com.panol_project.backendpanol.modules.loan.api.dto;

import java.util.UUID;

public record LoanRoomV2Response(
        UUID uuid,
        String name
) {
}

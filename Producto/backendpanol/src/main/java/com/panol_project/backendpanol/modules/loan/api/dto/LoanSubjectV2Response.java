package com.panol_project.backendpanol.modules.loan.api.dto;

import java.util.UUID;

public record LoanSubjectV2Response(
        UUID uuid,
        String name
) {
}

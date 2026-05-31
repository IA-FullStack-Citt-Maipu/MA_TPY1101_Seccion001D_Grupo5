package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;

public record LoanStateDatesView(
        OffsetDateTime approvedAt,
        OffsetDateTime preparedAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime completedAt,
        OffsetDateTime rejectedAt,
        OffsetDateTime cancelledAt,
        OffsetDateTime expiredAt,
        OffsetDateTime overdueAt
) {
}

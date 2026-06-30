package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;

public record LoanStatusTimelineEntry(
        Long historyId,
        LoanStatus fromStatus,
        LoanStatus toStatus,
        Long actorUserId,
        String actorName,
        String actorEmail,
        String notes,
        OffsetDateTime changedAt
) {
}

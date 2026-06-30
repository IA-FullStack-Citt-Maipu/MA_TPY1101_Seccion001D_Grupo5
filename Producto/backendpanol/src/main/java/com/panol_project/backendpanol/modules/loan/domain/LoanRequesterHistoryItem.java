package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanRequesterHistoryItem(
        UUID uuid,
        LoanStatus status,
        OffsetDateTime scheduledAt,
        OffsetDateTime expectedReturnAt,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        LoanStateDatesView stateDates,
        LoanSummaryView.RoomView room,
        LoanSummaryView.SubjectView subject,
        List<LoanSummaryView.ItemView> items
) {
}

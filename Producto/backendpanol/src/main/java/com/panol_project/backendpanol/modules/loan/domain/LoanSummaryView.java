package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanSummaryView(
        UUID uuid,
        UUID requesterUuid,
        LoanStatus status,
        OffsetDateTime scheduledAt,
        OffsetDateTime createdAt,
        RoomView room,
        SubjectView subject,
        List<ItemView> items
) {

    public record RoomView(
            UUID uuid,
            String name
    ) {
    }

    public record SubjectView(
            UUID uuid,
            String name
    ) {
    }

    public record ItemView(
            UUID implementUuid,
            String implementName,
            Integer requestedQuantity,
            Integer reservedQuantity,
            Integer deliveredQuantity
    ) {
    }
}

package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanSummaryView(
        UUID uuid,
        UUID requesterUuid,
        LoanStatus status,
        OffsetDateTime scheduledAt,
        OffsetDateTime expectedReturnAt,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
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
            String itemType,
            Integer requestedQuantity,
            Integer reservedQuantity,
            Integer deliveredQuantity,
            Integer returnedQuantity
    ) {
        public ItemView(
                UUID implementUuid,
                String implementName,
                Integer requestedQuantity,
                Integer reservedQuantity,
                Integer deliveredQuantity
        ) {
            this(implementUuid, implementName, null, requestedQuantity, reservedQuantity, deliveredQuantity, 0);
        }

        public ItemView(
                UUID implementUuid,
                String implementName,
                Integer requestedQuantity,
                Integer reservedQuantity,
                Integer deliveredQuantity,
                Integer returnedQuantity
        ) {
            this(implementUuid, implementName, null, requestedQuantity, reservedQuantity, deliveredQuantity, returnedQuantity);
        }
    }
}

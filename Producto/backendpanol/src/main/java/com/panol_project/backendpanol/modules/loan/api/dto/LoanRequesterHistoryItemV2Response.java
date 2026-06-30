package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanRequesterHistoryItemV2Response(
        UUID uuid,
        String status,
        @JsonProperty("scheduled_at")
        OffsetDateTime scheduledAt,
        @JsonProperty("expected_return_at")
        OffsetDateTime expectedReturnAt,
        @JsonProperty("created_at")
        OffsetDateTime createdAt,
        @JsonProperty("completed_at")
        OffsetDateTime completedAt,
        @JsonProperty("approved_at")
        OffsetDateTime approvedAt,
        @JsonProperty("prepared_at")
        OffsetDateTime preparedAt,
        @JsonProperty("delivered_at")
        OffsetDateTime deliveredAt,
        @JsonProperty("rejected_at")
        OffsetDateTime rejectedAt,
        @JsonProperty("cancelled_at")
        OffsetDateTime cancelledAt,
        @JsonProperty("expired_at")
        OffsetDateTime expiredAt,
        @JsonProperty("overdue_at")
        OffsetDateTime overdueAt,
        LoanRoomV2Response room,
        LoanSubjectV2Response subject,
        List<LoanItemV2Response> items
) {
}

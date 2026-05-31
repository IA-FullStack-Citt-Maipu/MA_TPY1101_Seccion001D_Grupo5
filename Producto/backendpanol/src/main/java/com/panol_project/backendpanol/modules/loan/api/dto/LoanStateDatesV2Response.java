package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record LoanStateDatesV2Response(
        @JsonProperty("approved_at")
        OffsetDateTime approvedAt,

        @JsonProperty("prepared_at")
        OffsetDateTime preparedAt,

        @JsonProperty("delivered_at")
        OffsetDateTime deliveredAt,

        @JsonProperty("completed_at")
        OffsetDateTime completedAt,

        @JsonProperty("rejected_at")
        OffsetDateTime rejectedAt,

        @JsonProperty("cancelled_at")
        OffsetDateTime cancelledAt,

        @JsonProperty("expired_at")
        OffsetDateTime expiredAt,

        @JsonProperty("overdue_at")
        OffsetDateTime overdueAt
) {
}

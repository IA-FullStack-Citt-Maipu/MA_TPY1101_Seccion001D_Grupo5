package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record LoanStatusTimelineEntryV2Response(
        @JsonProperty("history_id")
        Long historyId,

        @JsonProperty("from_status")
        String fromStatus,

        @JsonProperty("to_status")
        String toStatus,

        @JsonProperty("actor_user_id")
        Long actorUserId,

        @JsonProperty("actor_name")
        String actorName,

        @JsonProperty("actor_email")
        String actorEmail,

        String notes,

        @JsonProperty("changed_at")
        OffsetDateTime changedAt
) {
}

package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanV2Response(
        UUID uuid,

        @JsonProperty("requester_uuid")
        UUID requesterUuid,

        @JsonProperty("room_uuid")
        UUID roomUuid,

        @JsonProperty("subject_uuid")
        UUID subjectUuid,

        String status,

        @JsonProperty("scheduled_at")
        OffsetDateTime scheduledAt,

        @JsonProperty("due_date")
        OffsetDateTime dueDate,

        @JsonProperty("created_at")
        OffsetDateTime createdAt,

        List<LoanItemV2Response> items
) {
}

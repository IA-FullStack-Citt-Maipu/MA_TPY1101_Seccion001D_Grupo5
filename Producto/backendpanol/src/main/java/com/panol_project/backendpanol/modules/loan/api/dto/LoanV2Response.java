package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanV2Response(
        UUID uuid,

        @JsonProperty("requester_uuid")
        UUID requesterUuid,

        String status,

        @JsonProperty("scheduled_at")
        OffsetDateTime scheduledAt,

        @JsonProperty("expected_return_at")
        OffsetDateTime expectedReturnAt,

        @JsonProperty("created_at")
        OffsetDateTime createdAt,

        LoanRoomV2Response room,

        LoanSubjectV2Response subject,

        List<LoanItemV2Response> items
) {
}

package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanRequesterItemV2Response(
        @JsonProperty("requester_uuid")
        UUID requesterUuid,
        @JsonProperty("requester_name")
        String requesterName,
        @JsonProperty("requester_email")
        String requesterEmail,
        @JsonProperty("requester_rut")
        String requesterRut,
        @JsonProperty("last_loan_at")
        OffsetDateTime lastLoanAt,
        @JsonProperty("latest_loan_uuid")
        UUID latestLoanUuid,
        @JsonProperty("latest_status")
        String latestStatus,
        @JsonProperty("latest_room_name")
        String latestRoomName,
        @JsonProperty("latest_subject_name")
        String latestSubjectName,
        @JsonProperty("total_loans")
        int totalLoans,
        @JsonProperty("active_loans")
        int activeLoans
) {
}

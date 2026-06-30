package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoanRequesterSummary(
        UUID requesterUuid,
        String requesterName,
        String requesterEmail,
        String requesterRut,
        OffsetDateTime lastLoanAt,
        UUID latestLoanUuid,
        LoanStatus latestLoanStatus,
        String latestRoomName,
        String latestSubjectName,
        int totalLoans,
        int activeLoans
) {
}

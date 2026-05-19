package com.panol_project.backendpanol.modules.loan.domain;

import java.util.UUID;

public record LoanReviewCommand(
        UUID loanUuid,
        UUID actorUuid,
        LoanReviewDecision decision,
        String reviewNotes,
        String rejectionReason
) {
}

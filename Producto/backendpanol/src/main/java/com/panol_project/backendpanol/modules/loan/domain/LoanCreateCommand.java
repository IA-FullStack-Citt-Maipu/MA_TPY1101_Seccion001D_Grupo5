package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanCreateCommand(
        UUID requesterUuid,
        UUID roomUuid,
        UUID subjectUuid,
        OffsetDateTime scheduledAt,
        OffsetDateTime dueDate,
        List<LoanRequestedItem> requestedItems
) {
}

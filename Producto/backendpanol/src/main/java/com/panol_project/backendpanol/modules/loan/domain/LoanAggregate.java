package com.panol_project.backendpanol.modules.loan.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoanAggregate(
        UUID uuid,
        UUID requesterUuid,
        UUID roomUuid,
        UUID subjectUuid,
        LoanStatus status,
        OffsetDateTime scheduledAt,
        OffsetDateTime dueDate,
        OffsetDateTime createdAt,
        List<LoanDetailItem> items
) {
}

package com.panol_project.backendpanol.modules.loan.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SolicitarPrestamoCommand(
        UUID requesterUuid,
        UUID roomUuid,
        UUID subjectUuid,
        OffsetDateTime scheduledAt,
        OffsetDateTime dueDate,
        List<SolicitarPrestamoItemCommand> requestedItems
) {
}

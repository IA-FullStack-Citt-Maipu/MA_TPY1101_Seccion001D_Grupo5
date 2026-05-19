package com.panol_project.backendpanol.modules.loan.application.dto;

import java.util.UUID;

public record RevisarPrestamoCommand(
        UUID loanUuid,
        UUID actorUuid,
        String decision,
        String reviewNotes,
        String rejectionReason
) {
}

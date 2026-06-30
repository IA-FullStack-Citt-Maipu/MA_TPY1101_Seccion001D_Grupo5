package com.panol_project.backendpanol.shared.error;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp
) {
}

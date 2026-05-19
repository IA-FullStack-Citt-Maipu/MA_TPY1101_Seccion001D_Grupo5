package com.panol_project.backendpanol.modules.catalog.implement.application;

import java.time.Instant;
import java.util.UUID;

public record ImplementRecentMovement(
        String id,
        UUID implementUuid,
        String action,
        Integer quantity,
        UUID performedByUuid,
        String performedByName,
        Instant timestamp,
        String notes
) {
}

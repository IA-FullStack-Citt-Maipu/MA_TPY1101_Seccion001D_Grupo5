package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import java.util.UUID;

public record ImplementLocationSummaryV2Response(
        UUID uuid,
        String name,
        String description
) {
}

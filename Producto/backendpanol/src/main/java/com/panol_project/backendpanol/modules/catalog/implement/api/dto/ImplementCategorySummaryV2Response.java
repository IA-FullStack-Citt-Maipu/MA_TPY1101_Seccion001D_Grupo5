package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import java.util.UUID;

public record ImplementCategorySummaryV2Response(
        UUID uuid,
        String name,
        Boolean active
) {
}

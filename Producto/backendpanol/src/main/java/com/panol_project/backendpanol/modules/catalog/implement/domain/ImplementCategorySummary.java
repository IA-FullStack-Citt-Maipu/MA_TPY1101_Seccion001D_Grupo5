package com.panol_project.backendpanol.modules.catalog.implement.domain;

import java.util.UUID;

public record ImplementCategorySummary(
        UUID uuid,
        String name,
        Boolean active
) {
}


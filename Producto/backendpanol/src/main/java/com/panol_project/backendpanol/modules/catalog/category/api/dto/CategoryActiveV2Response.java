package com.panol_project.backendpanol.modules.catalog.category.api.dto;

import java.util.UUID;

public record CategoryActiveV2Response(
        UUID uuid,
        String name
) {
}

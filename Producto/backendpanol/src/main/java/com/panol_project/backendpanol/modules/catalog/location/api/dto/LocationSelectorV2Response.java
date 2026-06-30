package com.panol_project.backendpanol.modules.catalog.location.api.dto;

import java.util.UUID;

public record LocationSelectorV2Response(
        UUID uuid,
        String name,
        String description,
        Boolean active
) {
}

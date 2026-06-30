package com.panol_project.backendpanol.modules.catalog.location.domain;

import java.util.UUID;

public record LocationOption(
        UUID uuid,
        String name,
        String description,
        Boolean active
) {
}


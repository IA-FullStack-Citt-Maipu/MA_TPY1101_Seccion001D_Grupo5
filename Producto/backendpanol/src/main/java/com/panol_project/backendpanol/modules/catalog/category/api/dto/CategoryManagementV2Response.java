package com.panol_project.backendpanol.modules.catalog.category.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryManagementV2Response(
        UUID uuid,
        String nombre,
        String descripcion,
        Boolean activa,
        OffsetDateTime createdAt
) {
}

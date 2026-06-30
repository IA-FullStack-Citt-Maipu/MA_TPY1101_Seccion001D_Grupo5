package com.panol_project.backendpanol.modules.catalog.category.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Categoria(
        UUID uuid,
        String nombre,
        String descripcion,
        Boolean activa,
        OffsetDateTime createdAt
) {
}

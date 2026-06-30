package com.panol_project.backendpanol.modules.catalog.implement.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Implemento(
        UUID uuid,
        String nombre,
        String descripcion,
        UUID categoriaUuid,
        UUID locationUuid,
        ImplementItemType itemType,
        String barcode,
        String imgUrl,
        String observations,
        String costCenter,
        BigDecimal netValue,
        Boolean activo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

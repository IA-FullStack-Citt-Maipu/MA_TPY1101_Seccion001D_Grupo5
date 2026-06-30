package com.panol_project.backendpanol.modules.catalog.implement.domain;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ImplementRepository {

    Optional<Implemento> findByUuid(UUID uuid);

    Optional<ImplementSummary> findSummaryByUuid(UUID uuid);

    List<ImplementSummary> findAllSummaries(
            String name,
            UUID categoryUuid,
            StockStatusFilter stockStatusFilter,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            UUID excludeLoanUuid
    );

    boolean existsActiveByNameIgnoreCase(String nombre, UUID categoriaUuid);

    boolean existsActiveByNameIgnoreCaseAndUuidNot(String nombre, UUID categoriaUuid, UUID excludedUuid);

    Implemento create(
            String nombre,
            String descripcion,
            UUID categoriaUuid,
            UUID locationUuid,
            ImplementItemType itemType,
            String barcode,
            String imgUrl,
            String observations,
            String costCenter,
            BigDecimal netValue
    );

    Implemento update(
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
            BigDecimal netValue
    );

    int updateActive(UUID uuid, boolean active);

    int updateMinStockByImplementUuid(UUID implementUuid, Integer minStock);

    Optional<Integer> findMinStockByImplementUuid(UUID implementUuid);
}

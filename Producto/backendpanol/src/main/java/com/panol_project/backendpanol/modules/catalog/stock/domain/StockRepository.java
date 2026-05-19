package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    Optional<ImplementStockContext> findImplementContext(UUID implementUuid);

    void ensureStockRow(UUID implementUuid);

    Optional<StockCounters> findStockByImplementUuid(UUID implementUuid);

    List<IndividualItem> findActiveIndividualsByImplementUuid(UUID implementUuid);

    List<IndividualItem> findActiveIndividualsByUuids(UUID implementUuid, List<UUID> individualUuids);

    void createIndividuals(UUID implementUuid, UUID locationUuid, List<String> assetCodes);

    void updateStock(UUID implementUuid, int totalDelta, int availableDelta, int reservedDelta, int loanedDelta, int damagedDelta);

    void replaceStock(UUID implementUuid, int total, int available, int reserved, int loaned, int damaged);

    void updateIndividualsState(
            List<UUID> individualUuids,
            String statusLiteral,
            String conditionLiteral,
            String notes,
            UUID locationUuid,
            Boolean active
    );

    record ImplementStockContext(UUID implementUuid, UUID locationUuid, StockItemType itemType, Boolean active) {
    }
}

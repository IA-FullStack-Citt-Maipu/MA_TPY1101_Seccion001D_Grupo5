package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository {
    InventoryMovement save(InventoryMovement movement);
    List<InventoryMovement> findTop10ByImplementUuidOrderByTimestampDesc(UUID implementUuid);
    List<InventoryMovement> findByOrderByTimestampDesc(int limit);
    List<InventoryMovement> findAllByOrderByTimestampDesc();
}

package com.panol_project.backendpanol.modules.catalog.stock.application.contract;

import java.util.List;
import java.util.UUID;

public interface StockMovementContract {

    void applyMovement(
            UUID implementUuid,
            String movementType,
            Integer quantity,
            List<UUID> individualUuids,
            String condition
    );
}

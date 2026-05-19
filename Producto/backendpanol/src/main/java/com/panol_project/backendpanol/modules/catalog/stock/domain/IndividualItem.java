package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.UUID;

public record IndividualItem(
        UUID uuid,
        UUID implementUuid,
        String assetCode,
        String status,
        String condition,
        String notes,
        UUID currentLocationUuid,
        Boolean active
) {
}

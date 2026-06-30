package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.UUID;

public record IndividualEntryDraft(
        String assetCode,
        String status,
        String condition,
        UUID currentLocationUuid,
        Integer remainingLife,
        Boolean assetCodeReprintRequired
) {
}

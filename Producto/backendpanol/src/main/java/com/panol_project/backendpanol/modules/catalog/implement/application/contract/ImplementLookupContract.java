package com.panol_project.backendpanol.modules.catalog.implement.application.contract;

import java.util.UUID;

public interface ImplementLookupContract {

    ImplementLookupSummary obtenerImplementoParaStock(UUID implementUuid);

    record ImplementLookupSummary(
            UUID uuid,
            String name,
            String barcode,
            String itemTypeLiteral
    ) {
    }
}

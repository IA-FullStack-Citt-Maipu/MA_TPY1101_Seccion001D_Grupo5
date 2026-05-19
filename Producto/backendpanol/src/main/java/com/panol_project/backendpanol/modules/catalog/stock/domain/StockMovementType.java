package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.Optional;
import java.util.Locale;
import java.util.Arrays;

public enum StockMovementType {
    STOCK_IN,
    STOCK_OUT,
    LOAN_DELIVERY,
    LOAN_RETURN,
    DAMAGE_REPORT,
    MANUAL_ADJUSTMENT;

    public static Optional<StockMovementType> fromLiteral(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst();
    }
}

package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum StockItemType {
    FUNGIBLE("fungible"),
    NO_FUNGIBLE("no_fungible");

    private final String literal;

    StockItemType(String literal) {
        this.literal = literal;
    }

    public String literal() {
        return literal;
    }

    public static Optional<StockItemType> fromLiteral(String literal) {
        if (literal == null) {
            return Optional.empty();
        }
        String normalized = literal.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.literal.equals(normalized))
                .findFirst();
    }
}

package com.panol_project.backendpanol.modules.catalog.implement.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ImplementItemType {
    CONSUMABLE("consumable"),
    REUSABLE("reusable"),
    INDIVIDUAL("individual");

    private final String literal;

    ImplementItemType(String literal) {
        this.literal = literal;
    }

    public String literal() {
        return literal;
    }

    public static Optional<ImplementItemType> fromLiteral(String literal) {
        if (literal == null) {
            return Optional.empty();
        }

        String normalized = literal.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.literal.equals(normalized))
                .findFirst();
    }
}

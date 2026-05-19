package com.panol_project.backendpanol.modules.loan.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum LoanStatus {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    DELIVERED("delivered"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String literal;

    LoanStatus(String literal) {
        this.literal = literal;
    }

    public String literal() {
        return literal;
    }

    public static Optional<LoanStatus> fromLiteral(String literal) {
        if (literal == null) {
            return Optional.empty();
        }
        String normalized = literal.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.literal.equals(normalized))
                .findFirst();
    }
}

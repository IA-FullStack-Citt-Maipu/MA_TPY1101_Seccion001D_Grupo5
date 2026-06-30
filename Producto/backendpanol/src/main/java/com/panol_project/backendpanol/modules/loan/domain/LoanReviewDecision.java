package com.panol_project.backendpanol.modules.loan.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum LoanReviewDecision {
    APPROVE,
    REJECT;

    public static Optional<LoanReviewDecision> fromLiteral(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.name().equals(normalized))
                .findFirst();
    }
}

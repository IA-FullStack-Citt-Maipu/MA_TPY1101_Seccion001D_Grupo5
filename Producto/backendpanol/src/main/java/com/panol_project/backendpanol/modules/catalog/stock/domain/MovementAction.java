package com.panol_project.backendpanol.modules.catalog.stock.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum MovementAction {
    STOCK_IN("stock_in"),
    STOCK_OUT("stock_out"),
    LOAN_DELIVERY("loan_delivery"),
    LOAN_RETURN("loan_return"),
    DAMAGE_REPORT("damage_report"),
    MANUAL_ADJUSTMENT("manual_adjustment"),
    CONSUMPTION("consumption"),
    DISCARD("discard"),
    LOSS("loss");

    private final String literal;

    MovementAction(String literal) {
        this.literal = literal;
    }

    @JsonValue
    public String literal() {
        return literal;
    }

    @JsonCreator
    public static MovementAction fromJson(String raw) {
        return fromLiteral(raw)
                .orElseThrow(() -> new IllegalArgumentException("movement_action invalido: " + raw));
    }

    public static Optional<MovementAction> fromLiteral(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.literal.equals(normalized) || value.name().equalsIgnoreCase(normalized))
                .findFirst();
    }
}

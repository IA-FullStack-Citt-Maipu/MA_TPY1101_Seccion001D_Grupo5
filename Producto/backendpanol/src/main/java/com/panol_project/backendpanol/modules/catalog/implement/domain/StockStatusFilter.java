package com.panol_project.backendpanol.modules.catalog.implement.domain;

import java.util.Optional;

/**
 * Representa el filtro de estado de stock que puede aplicarse al catálogo de implementos.
 * Cada valor corresponde a una columna de la tabla stock que debe ser > 0.
 *
 * <p>Solo los valores {@code AVAILABLE}, {@code RESERVED}, {@code LOANED}
 * y {@code DAMAGED} son válidos para el filtro de stock.</p>
 */
public enum StockStatusFilter {
    AVAILABLE("available"),
    RESERVED("reserved"),
    LOANED("loaned"),
    DAMAGED("damaged");

    private final String value;

    StockStatusFilter(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Resuelve el enum desde el valor en string (case-insensitive).
     *
     * @param value el valor en string, ej: "available"
     * @return un Optional con el enum si el valor es válido
     */
    public static Optional<StockStatusFilter> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (StockStatusFilter filter : values()) {
            if (filter.value.equalsIgnoreCase(value)) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }
}

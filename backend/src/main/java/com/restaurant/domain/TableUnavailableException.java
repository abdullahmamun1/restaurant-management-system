package com.restaurant.domain;

/**
 * Raised when a table cannot take a new order because it is not {@code AVAILABLE} — i.e. it
 * already has an open, unpaid order (FR-06). Enforced in the domain and, as a second line of
 * defence, by the {@code uq_active_order_per_table} partial unique index. Mapped to HTTP 409.
 */
public class TableUnavailableException extends RuntimeException {

    public TableUnavailableException(String message) {
        super(message);
    }
}

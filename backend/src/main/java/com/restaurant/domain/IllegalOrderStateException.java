package com.restaurant.domain;

/**
 * Raised when an operation is illegal for the order's current {@link OrderStatus} — an
 * unsupported lifecycle transition, an item edit after {@code CONFIRMED} (FR-07, FR-09), or
 * confirming an empty order. Thrown by the domain so the rule holds for every caller, mirroring
 * {@link InsufficientStockException}. Mapped to HTTP 409 by the global handler.
 */
public class IllegalOrderStateException extends RuntimeException {

    public IllegalOrderStateException(String message) {
        super(message);
    }
}

package com.restaurant.domain;

/**
 * Raised when a report's date range cannot mean anything — currently, when {@code to} falls before
 * {@code from} (M7 D8).
 *
 * <p>Thrown by {@link DateRange}'s constructor so the rule holds for every caller, mirroring
 * {@link IllegalOrderStateException} and {@link InsufficientStockException}. Mapped to HTTP
 * <strong>400</strong> by the global handler — unlike those two, which are 409s: a reversed range is
 * a malformed request, not a conflict with the system's state.
 *
 * <p>Refusing it matters more than it looks. A reversed range produces an <em>empty</em> half-open
 * interval, so without this the endpoint would answer a nonsensical question with a confident
 * {@code 0.00} — the worst possible response for a report someone is about to act on.
 */
public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(String message) {
        super(message);
    }
}

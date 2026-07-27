package com.restaurant.domain;

/**
 * Dining-table occupancy states (SRS §2.4). Driven by the order lifecycle: creating an order
 * occupies the table (FR-06) and payment releases it (FR-15, M6). {@code NEEDS_SERVICE} is a
 * waiter-raised flag on an occupied table and does not affect the order lifecycle.
 */
public enum TableStatus {
    AVAILABLE,
    OCCUPIED,
    NEEDS_SERVICE
}

package com.restaurant.domain;

/**
 * Raised when an operation would drive an ingredient's stock below zero (FR-22). Thrown by the
 * domain so the invariant is enforced wherever stock changes — manual adjustment (M3) and the
 * automatic payment deduction (M6). Mapped to HTTP 409 by the global handler.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}

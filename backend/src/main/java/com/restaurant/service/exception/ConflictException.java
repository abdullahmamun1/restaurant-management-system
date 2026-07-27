package com.restaurant.service.exception;

/**
 * Thrown when a request conflicts with the current state — e.g. a duplicate name, or deleting
 * a category that still has items. Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

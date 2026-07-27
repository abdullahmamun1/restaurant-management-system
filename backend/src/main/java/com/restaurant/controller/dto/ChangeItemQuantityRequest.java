package com.restaurant.controller.dto;

import jakarta.validation.constraints.Min;

/**
 * Sets an existing order line's quantity on a PENDING order (FR-07) — the waiter's +/- controls.
 *
 * <p>An <strong>absolute</strong> quantity rather than a delta: several taps in quick succession
 * then converge on the right answer whatever order they reach the server in, where a stream of
 * "+1"s would not.
 *
 * <p>{@code @Min(1)} because zero is not a quantity, it is a removal — and that already has its
 * own endpoint. Sending 0 here is a 400 rather than a silent delete.
 */
public record ChangeItemQuantityRequest(@Min(1) int quantity) {
}

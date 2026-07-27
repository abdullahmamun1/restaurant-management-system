package com.restaurant.service.validation;

import com.restaurant.domain.MenuItem;
import com.restaurant.domain.Order;

/**
 * Immutable input passed along the FR-08 validation chain: the order being edited, the item the
 * waiter is trying to add, and how many.
 *
 * <p>The {@code order} is included so a link can account for what the order already commits — the
 * stock check needs the whole order's ingredient draw, not just this one line.
 */
public record OrderItemValidationContext(Order order, MenuItem menuItem, int quantity) {
}

package com.restaurant.controller.dto;

import java.math.BigDecimal;

/**
 * One line of an order. {@code unitPrice} is the price snapshotted when the line was added, not
 * the menu item's current price; {@code lineTotal} is derived server-side so the client never
 * recomputes money.
 */
public record OrderItemDto(
        Long id,
        Long menuItemId,
        String menuItemName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String notes) {
}

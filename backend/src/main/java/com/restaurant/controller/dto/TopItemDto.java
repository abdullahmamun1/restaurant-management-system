package com.restaurant.controller.dto;

import java.math.BigDecimal;

/**
 * One row of the top-selling menu items report (FR-24), ranked by {@code quantitySold} with ties
 * broken by name so the ordering is stable between identical requests.
 *
 * <p>{@code revenue} goes beyond FR-24's letter — it asks only for the ranking by quantity — but the
 * aggregate query already groups by menu item, so the sum is free and it turns a bare league table
 * into something a manager can act on.
 */
public record TopItemDto(
        Long menuItemId,
        String name,
        String categoryName,
        long quantitySold,
        BigDecimal revenue) {
}

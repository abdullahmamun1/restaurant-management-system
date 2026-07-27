package com.restaurant.controller.dto;

import java.math.BigDecimal;

/** Public view of a menu item, including its category for convenient display. */
public record MenuItemDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Long categoryId,
        String categoryName,
        boolean available) {
}

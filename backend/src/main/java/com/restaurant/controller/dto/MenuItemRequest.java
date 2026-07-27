package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Create/update payload for a menu item (FR-04). */
public record MenuItemRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 400) String description,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotNull Long categoryId,
        boolean available) {
}

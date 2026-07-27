package com.restaurant.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds a menu item to a PENDING order (FR-07). Notes are optional preparation instructions for
 * the kitchen. Availability and stock are validated server-side before anything is written (FR-08).
 */
public record AddOrderItemRequest(
        @NotNull Long menuItemId,
        @Min(1) int quantity,
        @Size(max = 300) String notes) {
}

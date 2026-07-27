package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotNull;

/** Opens a new order on a table (FR-06). The waiter is taken from the authenticated principal. */
public record CreateOrderRequest(@NotNull Long tableId) {
}

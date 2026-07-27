package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Update payload for an ingredient — descriptive fields only. Stock is intentionally absent:
 * it changes solely through audited adjustments (FR-21, NFR-06).
 */
public record IngredientUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @PositiveOrZero BigDecimal lowStockThreshold) {
}

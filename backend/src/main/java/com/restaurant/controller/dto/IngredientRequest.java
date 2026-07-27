package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Create payload for an ingredient (FR-17). Initial stock is set once here. */
public record IngredientRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @PositiveOrZero BigDecimal initialStock,
        @NotNull @PositiveOrZero BigDecimal lowStockThreshold) {
}

package com.restaurant.controller.dto;

import java.math.BigDecimal;

/** Public view of an ingredient, including a computed low-stock flag (FR-18). */
public record IngredientDto(
        Long id,
        String name,
        String unit,
        BigDecimal stockQty,
        BigDecimal lowStockThreshold,
        boolean lowStock) {
}

package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** One requested recipe line: an ingredient and the quantity consumed per menu-item unit. */
public record RecipeLineRequest(
        @NotNull Long ingredientId,
        @NotNull @Positive BigDecimal quantity) {
}

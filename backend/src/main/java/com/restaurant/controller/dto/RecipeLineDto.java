package com.restaurant.controller.dto;

import java.math.BigDecimal;

/** One line of a recipe as returned to the client, with ingredient details for display. */
public record RecipeLineDto(
        Long ingredientId,
        String ingredientName,
        String unit,
        BigDecimal quantity) {
}

package com.restaurant.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Replaces a menu item's entire recipe with the given lines (atomic). */
public record RecipeRequest(@NotNull @Valid List<RecipeLineRequest> lines) {
}

package com.restaurant.controller.dto;

import java.util.List;

/** A menu item's full recipe (FR-19 basis). */
public record RecipeDto(Long menuItemId, String menuItemName, List<RecipeLineDto> lines) {
}

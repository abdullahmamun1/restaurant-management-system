package com.restaurant.service;

import com.restaurant.controller.dto.AdjustmentDto;
import com.restaurant.controller.dto.IngredientDto;
import com.restaurant.controller.dto.RecipeDto;
import com.restaurant.controller.dto.RecipeLineDto;
import com.restaurant.domain.Ingredient;
import com.restaurant.domain.InventoryAdjustment;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.RecipeItem;
import java.util.List;
import org.springframework.stereotype.Component;

/** Maps inventory/recipe entities to boundary DTOs (DTO + Mapper pattern). */
@Component
public class InventoryMapper {

    public IngredientDto toDto(Ingredient ingredient) {
        return new IngredientDto(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getUnit(),
                ingredient.getStockQty(),
                ingredient.getLowStockThreshold(),
                ingredient.isLowStock());
    }

    public AdjustmentDto toDto(InventoryAdjustment adj) {
        return new AdjustmentDto(
                adj.getId(),
                adj.getQuantityChange(),
                adj.getReason(),
                adj.getResultingStock(),
                adj.getManager().getUsername(),
                adj.getCreatedAt());
    }

    public RecipeLineDto toLineDto(RecipeItem line) {
        Ingredient ing = line.getIngredient();
        return new RecipeLineDto(ing.getId(), ing.getName(), ing.getUnit(), line.getQuantity());
    }

    public RecipeDto toRecipeDto(MenuItem menuItem, List<RecipeItem> lines) {
        return new RecipeDto(
                menuItem.getId(),
                menuItem.getName(),
                lines.stream().map(this::toLineDto).toList());
    }
}

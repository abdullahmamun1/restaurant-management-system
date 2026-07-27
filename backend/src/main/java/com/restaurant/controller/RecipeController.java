package com.restaurant.controller;

import com.restaurant.controller.dto.RecipeDto;
import com.restaurant.controller.dto.RecipeRequest;
import com.restaurant.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recipe mapping for a menu item (FR-19 basis). A recipe is internal inventory data, so it is
 * Manager-only — kept separate from the Waiter-readable menu endpoints in {@code MenuController}.
 */
@RestController
@RequestMapping("/menu/items/{itemId}/recipe")
@PreAuthorize("hasRole('MANAGER')")
public class RecipeController {

    private final InventoryService inventoryService;

    public RecipeController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public RecipeDto get(@PathVariable Long itemId) {
        return inventoryService.getRecipe(itemId);
    }

    @PutMapping
    public RecipeDto replace(@PathVariable Long itemId, @Valid @RequestBody RecipeRequest request) {
        return inventoryService.replaceRecipe(itemId, request);
    }
}

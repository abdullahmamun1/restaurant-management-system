package com.restaurant.controller;

import com.restaurant.controller.dto.AdjustmentDto;
import com.restaurant.controller.dto.AdjustmentRequest;
import com.restaurant.controller.dto.IngredientDto;
import com.restaurant.controller.dto.IngredientRequest;
import com.restaurant.controller.dto.IngredientUpdateRequest;
import com.restaurant.service.InventoryService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory Management endpoints (FR-17, FR-18, FR-21). Manager-only (SRS §2.1) — enforced
 * server-side with {@code @PreAuthorize} (NFR-03). Thin controller over {@link InventoryService}.
 */
@RestController
@RequestMapping("/inventory")
@PreAuthorize("hasRole('MANAGER')")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/ingredients")
    public List<IngredientDto> list() {
        return inventoryService.listIngredients();
    }

    @GetMapping("/ingredients/low-stock")
    public List<IngredientDto> lowStock() {
        return inventoryService.listLowStock();
    }

    @PostMapping("/ingredients")
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientDto create(@Valid @RequestBody IngredientRequest request) {
        return inventoryService.createIngredient(request);
    }

    @PutMapping("/ingredients/{id}")
    public IngredientDto update(@PathVariable Long id,
                                @Valid @RequestBody IngredientUpdateRequest request) {
        return inventoryService.updateIngredient(id, request);
    }

    @DeleteMapping("/ingredients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        inventoryService.deleteIngredient(id);
    }

    @PostMapping("/ingredients/{id}/adjustments")
    public IngredientDto adjust(@PathVariable Long id,
                                @Valid @RequestBody AdjustmentRequest request,
                                Principal principal) {
        return inventoryService.adjustStock(id, request.quantityChange(), request.reason(),
                principal.getName());
    }

    @GetMapping("/ingredients/{id}/adjustments")
    public List<AdjustmentDto> adjustments(@PathVariable Long id) {
        return inventoryService.getAdjustments(id);
    }
}

package com.restaurant.service;

import com.restaurant.controller.dto.AdjustmentDto;
import com.restaurant.controller.dto.IngredientDto;
import com.restaurant.controller.dto.IngredientRequest;
import com.restaurant.controller.dto.IngredientUpdateRequest;
import com.restaurant.controller.dto.RecipeDto;
import com.restaurant.controller.dto.RecipeLineRequest;
import com.restaurant.controller.dto.RecipeRequest;
import com.restaurant.domain.Ingredient;
import com.restaurant.domain.InventoryAdjustment;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.RecipeItem;
import com.restaurant.domain.User;
import com.restaurant.repository.IngredientRepository;
import com.restaurant.repository.InventoryAdjustmentRepository;
import com.restaurant.repository.MenuItemRepository;
import com.restaurant.repository.RecipeItemRepository;
import com.restaurant.repository.UserRepository;
import com.restaurant.service.exception.ConflictException;
import com.restaurant.service.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory & recipe business logic (FR-17, FR-18, FR-21, FR-22). All rules live here.
 * Stock changes only through {@link #adjustStock} — which locks the ingredient row (NFR-07),
 * enforces the non-negative invariant via the domain (FR-22), and appends an immutable audit
 * record (FR-21, NFR-06). Recipes are replaced atomically.
 */
@Service
public class InventoryService {

    private final IngredientRepository ingredientRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final InventoryMapper mapper;

    public InventoryService(IngredientRepository ingredientRepository,
                            RecipeItemRepository recipeItemRepository,
                            InventoryAdjustmentRepository adjustmentRepository,
                            MenuItemRepository menuItemRepository,
                            UserRepository userRepository,
                            InventoryMapper mapper) {
        this.ingredientRepository = ingredientRepository;
        this.recipeItemRepository = recipeItemRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    // ---- Ingredients (FR-17, FR-18) ----------------------------------------

    @Transactional(readOnly = true)
    public List<IngredientDto> listIngredients() {
        return ingredientRepository.findAllByOrderByNameAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<IngredientDto> listLowStock() {
        return ingredientRepository.findLowStock().stream().map(mapper::toDto).toList();
    }

    @Transactional
    public IngredientDto createIngredient(IngredientRequest request) {
        if (ingredientRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("An ingredient named '" + request.name() + "' already exists.");
        }
        Ingredient saved = ingredientRepository.save(new Ingredient(
                request.name(), request.unit(), request.initialStock(), request.lowStockThreshold()));
        return mapper.toDto(saved);
    }

    @Transactional
    public IngredientDto updateIngredient(Long id, IngredientUpdateRequest request) {
        Ingredient ingredient = requireIngredient(id);
        if (ingredientRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("An ingredient named '" + request.name() + "' already exists.");
        }
        ingredient.updateDetails(request.name(), request.unit(), request.lowStockThreshold());
        return mapper.toDto(ingredient);
    }

    @Transactional
    public void deleteIngredient(Long id) {
        Ingredient ingredient = requireIngredient(id);
        if (recipeItemRepository.existsByIngredientId(id)) {
            throw new ConflictException(
                    "Ingredient '" + ingredient.getName() + "' is used by one or more recipes. "
                            + "Remove it from those recipes before deleting it.");
        }
        ingredientRepository.delete(ingredient);
    }

    // ---- Adjustments (FR-21, FR-22, NFR-06, NFR-07) ------------------------

    /**
     * Applies a signed manual stock change and records it in the audit log. The ingredient row
     * is locked for the duration (NFR-07); the non-negative invariant is enforced by the domain
     * (FR-22) — an offending adjustment throws and the whole transaction rolls back.
     */
    @Transactional
    public IngredientDto adjustStock(Long ingredientId, BigDecimal delta, String reason,
                                     String managerUsername) {
        Ingredient ingredient = ingredientRepository.findByIdForUpdate(ingredientId)
                .orElseThrow(() -> notFound(ingredientId));
        User manager = userRepository.findByUsername(managerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + managerUsername + "' not found."));

        BigDecimal resulting = ingredient.applyAdjustment(delta); // throws if it would go negative
        adjustmentRepository.save(
                new InventoryAdjustment(ingredient, manager, delta, reason, resulting));
        return mapper.toDto(ingredient);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentDto> getAdjustments(Long ingredientId) {
        requireIngredient(ingredientId);
        return adjustmentRepository.findByIngredientIdOrderByCreatedAtDesc(ingredientId)
                .stream().map(mapper::toDto).toList();
    }

    // ---- Recipes (FR-19 basis) ---------------------------------------------

    @Transactional(readOnly = true)
    public RecipeDto getRecipe(Long menuItemId) {
        MenuItem menuItem = requireMenuItem(menuItemId);
        return mapper.toRecipeDto(menuItem, recipeItemRepository.findByMenuItemId(menuItemId));
    }

    /** Replaces a menu item's entire recipe atomically (delete-then-insert in one transaction). */
    @Transactional
    public RecipeDto replaceRecipe(Long menuItemId, RecipeRequest request) {
        MenuItem menuItem = requireMenuItem(menuItemId);

        Set<Long> seen = new HashSet<>();
        for (RecipeLineRequest line : request.lines()) {
            if (!seen.add(line.ingredientId())) {
                throw new ConflictException("Ingredient " + line.ingredientId()
                        + " appears more than once in the recipe.");
            }
        }

        recipeItemRepository.deleteByMenuItemId(menuItemId);
        recipeItemRepository.flush();

        for (RecipeLineRequest line : request.lines()) {
            Ingredient ingredient = requireIngredient(line.ingredientId());
            recipeItemRepository.save(new RecipeItem(menuItem, ingredient, line.quantity()));
        }
        return mapper.toRecipeDto(menuItem, recipeItemRepository.findByMenuItemId(menuItemId));
    }

    // ---- helpers -----------------------------------------------------------

    private Ingredient requireIngredient(Long id) {
        return ingredientRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private MenuItem requireMenuItem(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item " + id + " not found."));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Ingredient " + id + " not found.");
    }
}

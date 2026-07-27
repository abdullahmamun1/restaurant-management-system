package com.restaurant.service;

import com.restaurant.domain.Order;
import com.restaurant.domain.OrderItem;
import com.restaurant.domain.RecipeItem;
import com.restaurant.repository.RecipeItemRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * How much of each ingredient an order consumes: its lines crossed with the recipes of the menu
 * items on them (FR-19's basis).
 *
 * <p><strong>One definition, two callers</strong> — the FR-08 stock check that runs when a waiter
 * adds an item, and the FR-19 deduction that runs when the cashier takes payment. That is not
 * tidiness: if the check and the deduction ever computed the draw differently, an order would pass
 * validation and then fail at payment, or — far worse — deduct a different quantity than the one
 * it was checked against. Sharing the function makes disagreement impossible by construction.
 *
 * <p>The stock validator layers its own extra step on top (the quantity the waiter is
 * <em>requesting</em>, which is not yet on the order); that is a different question and stays
 * there.
 *
 * <p>A menu item with no recipe contributes nothing. Recipes are optional by design — an unmapped
 * item simply cannot run out — so its absence is silence, not an error.
 */
@Component
public class OrderIngredientDraw {

    private final RecipeItemRepository recipeItemRepository;

    public OrderIngredientDraw(RecipeItemRepository recipeItemRepository) {
        this.recipeItemRepository = recipeItemRepository;
    }

    /**
     * The ingredient quantities this order's current lines consume, keyed by ingredient id.
     *
     * <p>Must be called inside the service transaction: it walks {@link Order#getItems()}, which
     * is lazy.
     */
    public Map<Long, BigDecimal> forOrder(Order order) {
        Map<Long, Integer> unitsByMenuItem = new HashMap<>();
        for (OrderItem line : order.getItems()) {
            unitsByMenuItem.merge(line.getMenuItem().getId(), line.getQuantity(), Integer::sum);
        }
        return forMenuItemQuantities(unitsByMenuItem);
    }

    /**
     * The ingredient quantities a set of {@code menuItemId -> units} consumes. Exposed for the
     * FR-08 validator, which needs to ask about a hypothetical basket (the order's lines plus the
     * line being requested) rather than an order as it stands.
     *
     * <p>One query for every recipe involved, not one per menu item — the validator runs on every
     * tap of the waiter's order builder.
     */
    public Map<Long, BigDecimal> forMenuItemQuantities(Map<Long, Integer> unitsByMenuItem) {
        if (unitsByMenuItem.isEmpty()) {
            return Map.of();
        }
        List<RecipeItem> recipes = recipeItemRepository.findByMenuItemIdIn(unitsByMenuItem.keySet());

        // LinkedHashMap so the draw is stable to iterate for messages and tests; the deduction
        // itself re-sorts by ingredient id for lock ordering (NFR-07) and does not rely on this.
        Map<Long, BigDecimal> draw = new LinkedHashMap<>();
        for (RecipeItem recipe : recipes) {
            Integer units = unitsByMenuItem.get(recipe.getMenuItem().getId());
            if (units == null) {
                continue;
            }
            draw.merge(
                    recipe.getIngredient().getId(),
                    recipe.getQuantity().multiply(BigDecimal.valueOf(units)),
                    BigDecimal::add);
        }
        return draw;
    }
}

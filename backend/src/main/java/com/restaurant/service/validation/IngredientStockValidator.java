package com.restaurant.service.validation;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.InsufficientStockException;
import com.restaurant.domain.OrderItem;
import com.restaurant.repository.IngredientRepository;
import com.restaurant.service.OrderIngredientDraw;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Second link of the FR-08 chain: enough ingredient stock must exist to fulfil the requested
 * quantity (FR-08b).
 *
 * <p><strong>What "enough" means here.</strong> Stock is only deducted at payment (FR-19), so
 * nothing is reserved in the meantime. This link therefore checks the requested quantity
 * <em>plus the draw already committed by the rest of this order</em> against on-hand stock.
 * Aggregating across the order matters: without it a waiter could add one unit ten times, pass
 * every individual check, and end up with an order that cannot be fulfilled. Two different menu
 * items sharing an ingredient are likewise summed.
 *
 * <p>The recipe arithmetic itself is {@link OrderIngredientDraw}, shared with M6's payment
 * deduction (FR-19). Sharing it is the point: if the check and the deduction computed the draw
 * differently, an order could pass here and then fail — or deduct a different amount than it was
 * checked against — at the till. What stays local to this link is the hypothetical basket it asks
 * about: the order's existing lines <em>plus</em> the line the waiter is requesting, which is not
 * on the order yet.
 *
 * <p>Stock is <em>not</em> reserved across orders — a reservation model is outside the SRS, and
 * M6's payment transaction is the authoritative point: it locks each ingredient row and rolls the
 * whole payment back if a deduction would go negative (FR-22, NFR-04, NFR-07).
 *
 * <p>Deliberately read-only, with no {@code SELECT ... FOR UPDATE}: holding ingredient row locks
 * while a waiter browses the menu would serialize the entire floor.
 */
@Component
@Order(20)
public class IngredientStockValidator implements OrderItemValidator {

    private final OrderIngredientDraw ingredientDraw;
    private final IngredientRepository ingredientRepository;

    public IngredientStockValidator(OrderIngredientDraw ingredientDraw,
                                    IngredientRepository ingredientRepository) {
        this.ingredientDraw = ingredientDraw;
        this.ingredientRepository = ingredientRepository;
    }

    @Override
    public void validate(OrderItemValidationContext context) {
        Map<Long, BigDecimal> required =
                ingredientDraw.forMenuItemQuantities(totalQuantityPerMenuItem(context));
        if (required.isEmpty()) {
            // No recipe on anything in the basket: nothing consumes stock, nothing to check.
            return;
        }

        // One read for the ingredients involved. The draw deliberately returns ids rather than
        // entities, because the two callers need them loaded differently — this link reads them,
        // while the FR-19 deduction takes a row lock on each one.
        for (Ingredient ingredient : ingredientRepository.findAllById(required.keySet())) {
            BigDecimal needed = required.get(ingredient.getId());
            if (needed != null && needed.compareTo(ingredient.getStockQty()) > 0) {
                throw new InsufficientStockException(shortfallMessage(context, ingredient, needed));
            }
        }
    }

    /** The requested quantity merged with what the order's existing lines already commit. */
    private Map<Long, Integer> totalQuantityPerMenuItem(OrderItemValidationContext context) {
        Map<Long, Integer> quantities = new HashMap<>();
        quantities.merge(context.menuItem().getId(), context.quantity(), Integer::sum);
        for (OrderItem line : context.order().getItems()) {
            quantities.merge(line.getMenuItem().getId(), line.getQuantity(), Integer::sum);
        }
        return quantities;
    }

    private String shortfallMessage(OrderItemValidationContext context, Ingredient ingredient,
                                    BigDecimal required) {
        return "Not enough '" + ingredient.getName() + "' to add "
                + context.quantity() + " × '" + context.menuItem().getName() + "'. This order needs "
                + plain(required) + " " + ingredient.getUnit() + " but only "
                + plain(ingredient.getStockQty()) + " " + ingredient.getUnit() + " is in stock.";
    }

    /** {@code 300.000} reads badly in an error a waiter has to act on — trim it to {@code 300}. */
    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

package com.restaurant.service;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.InsufficientStockException;
import com.restaurant.domain.Order;
import com.restaurant.repository.IngredientRepository;
import com.restaurant.service.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deducts every ingredient a paid order consumes (FR-19). The safety-critical path of the system.
 *
 * <p><strong>Transaction propagation is the whole point of this class's contract.</strong> It runs
 * inside the caller's transaction — default {@code REQUIRED} propagation — so the deduction, the
 * payment row and the status changes commit or roll back as one (NFR-04). <strong>Nothing on this
 * path may use {@code REQUIRES_NEW}.</strong> "Give the deduction its own transaction" is a
 * plausible-sounding refactor and it would silently destroy the all-or-nothing guarantee: a nested
 * transaction commits independently, leaving stock deducted for a payment that later rolled back.
 *
 * <p><strong>Lock ordering.</strong> Ingredient rows are locked one at a time in <em>ascending id
 * order</em> (NFR-07). A globally consistent acquisition order is what makes deadlock structurally
 * impossible: two payments touching overlapping ingredients in opposite orders would otherwise
 * deadlock in PostgreSQL, and because that is load-dependent it would not show up in casual
 * testing. A single batched {@code IN (…) FOR UPDATE ORDER BY id} would be one round-trip instead
 * of N, but PostgreSQL does not guarantee lock acquisition follows the {@code ORDER BY} under
 * every plan, and N is a handful of ingredients per order. Provable beats fast here.
 *
 * <p><strong>The FR-22 guard is not here.</strong> It is {@link Ingredient#applyAdjustment}, on the
 * entity, unchanged since M3 — this service adds no non-negativity logic of its own, which is
 * exactly the payoff of having put the invariant on the domain object. A deduction that would go
 * negative throws {@code InsufficientStockException}, which rolls the transaction back and reaches
 * the cashier as a 409 naming the ingredient.
 *
 * <p>Automatic deductions are deliberately <strong>not</strong> written to
 * {@code inventory_adjustment}: FR-21 and NFR-06 scope that log to <em>manual</em> adjustments and
 * its {@code manager_id} is {@code NOT NULL}, so writing payments there would mean either
 * weakening that column or recording a cashier as the acting manager. The {@code payment} row plus
 * the order's lines are the record of an automatic deduction. See D3 in {@code docs/M6-plan.md}
 * for the traceability gap this knowingly leaves.
 */
@Service
public class InventoryDeductionService {

    private final OrderIngredientDraw ingredientDraw;
    private final IngredientRepository ingredientRepository;
    /** Needed for one thing only: re-reading a locked row — see the comment in {@link #deductForOrder}. */
    private final EntityManager entityManager;

    public InventoryDeductionService(OrderIngredientDraw ingredientDraw,
                                     IngredientRepository ingredientRepository,
                                     EntityManager entityManager) {
        this.ingredientDraw = ingredientDraw;
        this.ingredientRepository = ingredientRepository;
        this.entityManager = entityManager;
    }

    /**
     * Applies the order's full ingredient draw to stock, inside the caller's transaction.
     *
     * @return the ingredients whose stock changed, so the caller can check them against their
     *         low-stock thresholds (FR-18) after the fact.
     * @throws com.restaurant.domain.InsufficientStockException if any deduction would take an
     *         ingredient below zero (FR-22) — the caller's whole transaction then rolls back, so
     *         nothing is deducted, no payment is recorded, and the order stays SERVED.
     */
    @Transactional
    public List<Ingredient> deductForOrder(Order order) {
        Map<Long, BigDecimal> draw = ingredientDraw.forOrder(order);

        List<Ingredient> touched = new ArrayList<>();
        // Ascending id: the deterministic acquisition order that makes deadlock impossible.
        for (Long ingredientId : draw.keySet().stream().sorted().toList()) {
            Ingredient ingredient = ingredientRepository.findByIdForUpdate(ingredientId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Ingredient " + ingredientId + " is on a recipe for this order but no "
                                    + "longer exists; the order cannot be settled."));

            // Re-read the row we just locked. This line is not defensive padding — without it the
            // deduction is a lost update, and the NFR-07 probe caught exactly that:
            // `forOrder` above runs the recipe query, and `RecipeItem.ingredient` is EAGER, so
            // every Ingredient is already in the persistence context by the time we get here.
            // `findByIdForUpdate` does acquire the row lock, but for an entity Hibernate is
            // already managing it returns that instance with the field values it read BEFORE the
            // lock. Two concurrent payments therefore both saw the pre-lock stock, both passed the
            // FR-22 check, and both wrote the same result — one payment's deduction silently
            // vanished. Refreshing inside the lock is what makes the value we do arithmetic on the
            // value the lock is protecting.
            //
            // Safe to refresh here because nothing earlier in the payment transaction modifies an
            // Ingredient, so there are no pending changes for it to discard, and each ingredient is
            // visited exactly once (the draw is keyed by id).
            entityManager.refresh(ingredient);

            BigDecimal required = draw.get(ingredientId);
            try {
                ingredient.applyAdjustment(required.negate());
            } catch (InsufficientStockException e) {
                // The guard is the entity's; only the wording is ours. Ingredient.applyAdjustment
                // phrases its message for a manager making a manual adjustment ("Adjustment of
                // -400 would drop ..."), which means nothing to a cashier who adjusted nothing.
                // FR-22 says halt *and notify the responsible party* — so the notification has to
                // name the shortfall and say who can clear it, because the cashier cannot: there
                // are no refunds or cancellations to fall back on (D9).
                throw new InsufficientStockException(shortfallMessage(ingredient, required));
            }
            touched.add(ingredient);
        }
        return touched;
    }

    /** Phrased for the till: what ran out, by how much, and who can clear it (FR-22, D9). */
    private String shortfallMessage(Ingredient ingredient, BigDecimal required) {
        return "Not enough '" + ingredient.getName() + "' to settle this order: it needs "
                + plain(required) + " " + ingredient.getUnit() + " but only "
                + plain(ingredient.getStockQty()) + " " + ingredient.getUnit()
                + " is in stock. A manager must restock it before this bill can be paid.";
    }

    /** {@code 400.000} reads badly in an error someone has to act on — trim it to {@code 400}. */
    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

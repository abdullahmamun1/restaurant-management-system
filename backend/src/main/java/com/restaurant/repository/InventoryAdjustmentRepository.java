package com.restaurant.repository;

import com.restaurant.domain.InventoryAdjustment;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Append-only persistence for the FR-21 inventory audit log (Repository pattern).
 *
 * <p><strong>Deliberately extends {@link Repository}, not {@code JpaRepository}.</strong> The
 * latter inherits {@code delete}, {@code deleteById}, {@code deleteAll} and friends, which would
 * leave NFR-06's "records shall not be editable or deletable" resting on nobody ever calling them —
 * a property of today's code rather than of the design. Only the two operations an audit log
 * legitimately has are declared here, so the rest cannot be called because they do not exist.
 *
 * <p>The {@code trg_inventory_adjustment_append_only} trigger ({@code V8__audit_immutability.sql})
 * is the other half, and it is the half that holds against callers this interface does not control:
 * a migration, a console session, or a future mapping mistake. Same belt-and-braces treatment as
 * FR-22's non-negative stock, guarded by both {@code Ingredient.applyAdjustment} and a CHECK
 * constraint.
 *
 * <p>{@link InventoryAdjustment} itself has no setters, so even a {@code save} of an already-managed
 * instance has nothing changed to write back.
 */
public interface InventoryAdjustmentRepository extends Repository<InventoryAdjustment, Long> {

    /** Appends a record. The only write this log has. */
    InventoryAdjustment save(InventoryAdjustment adjustment);

    /** One ingredient's history, most recent first — the manager's audit view (FR-21). */
    List<InventoryAdjustment> findByIngredientIdOrderByCreatedAtDesc(Long ingredientId);
}

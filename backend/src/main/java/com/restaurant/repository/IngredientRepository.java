package com.restaurant.repository;

import com.restaurant.domain.Ingredient;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Persistence access for {@link Ingredient} (Repository pattern). */
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Ingredients at or below their low-stock threshold (FR-18 / FR-20). */
    @Query("select i from Ingredient i where i.stockQty <= i.lowStockThreshold order by i.name")
    List<Ingredient> findLowStock();

    /**
     * Loads an ingredient with a pessimistic write lock ({@code SELECT ... FOR UPDATE}) so
     * concurrent stock changes for the same ingredient are serialized (NFR-07). Used by the
     * manual adjustment path and by the M6 payment deduction.
     *
     * <p><strong>Caution — this locks the row but does not guarantee fresh state.</strong> If the
     * entity is <em>already</em> in the persistence context, Hibernate returns the instance it is
     * managing, with the values it read before the lock was taken; the query still locks the row,
     * so the staleness is invisible until two transactions collide. Any caller that may have
     * loaded the ingredient earlier in the same transaction — directly, or through an eager
     * association such as {@code RecipeItem.ingredient} — must re-read it inside the lock with
     * {@code EntityManager.refresh}. {@code InventoryDeductionService} does exactly that, and
     * documents the lost update that happens without it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.id = :id")
    Optional<Ingredient> findByIdForUpdate(Long id);
}

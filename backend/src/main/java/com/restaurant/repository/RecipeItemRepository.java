package com.restaurant.repository;

import com.restaurant.domain.RecipeItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence access for {@link RecipeItem} (Repository pattern). */
public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    List<RecipeItem> findByMenuItemId(Long menuItemId);

    /**
     * Recipes for several menu items at once — lets the M4 stock validator aggregate an entire
     * order's ingredient draw in one query instead of one per line (FR-08).
     */
    List<RecipeItem> findByMenuItemIdIn(Collection<Long> menuItemIds);

    /** True if any recipe uses this ingredient — blocks deleting an in-use ingredient. */
    boolean existsByIngredientId(Long ingredientId);

    @Transactional
    void deleteByMenuItemId(Long menuItemId);
}

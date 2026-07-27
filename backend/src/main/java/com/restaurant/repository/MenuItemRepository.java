package com.restaurant.repository;

import com.restaurant.domain.MenuItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link MenuItem} (Repository pattern). */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByCategoryIdOrderByNameAsc(Long categoryId);

    List<MenuItem> findByAvailableTrueOrderByNameAsc();

    /** True if the category still has items — used to block deleting a non-empty category. */
    boolean existsByCategoryId(Long categoryId);

    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategoryIdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);
}

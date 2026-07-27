package com.restaurant.repository;

import com.restaurant.domain.MenuCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for {@link MenuCategory} (Repository pattern). */
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

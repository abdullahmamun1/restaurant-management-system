package com.restaurant.repository;

import com.restaurant.domain.RestaurantTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Persistence access for {@link RestaurantTable} (Repository pattern). */
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findAllByOrderByLabelAsc();

    /**
     * Loads a table with a pessimistic write lock ({@code SELECT ... FOR UPDATE}) so two waiters
     * opening an order on the same table are serialized rather than both seeing it AVAILABLE
     * (NFR-07). Mirrors {@code IngredientRepository.findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(Long id);
}

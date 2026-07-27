package com.restaurant.repository;

import com.restaurant.domain.Order;
import com.restaurant.domain.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence access for the {@link Order} aggregate (Repository pattern).
 *
 * <p>There is deliberately no {@code OrderItemRepository}: lines are owned by the {@link Order}
 * aggregate root and are persisted by cascade, so routing them through their own repository would
 * let callers mutate lines behind the FR-07/FR-09 edit guards.
 *
 * <p>The plain list finders leave {@code items} lazy; callers map them inside the service
 * transaction. That is one query per order on a list endpoint, which is fine at a single
 * restaurant's scale. The two paths that <em>are</em> under load fetch-join instead:
 * {@link #findByIdWithItems} (the order the waiter is editing) and {@link #findQueueWithItems}
 * (the kitchen queue, polled every few seconds).
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** The order plus its lines and their menu items, in one query (avoids N+1 on the hot path). */
    @Query("""
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.menuItem
            where o.id = :id
            """)
    Optional<Order> findByIdWithItems(Long id);

    /**
     * The open (unpaid) order on a table, if any. Backed by the {@code uq_active_order_per_table}
     * partial unique index, which is what makes "at most one" safe to rely on (FR-06).
     */
    Optional<Order> findByTableIdAndStatusNot(Long tableId, OrderStatus status);

    /** Every open order — used to annotate the floor view with each table's active order. */
    List<Order> findByStatusNot(OrderStatus status);

    /**
     * The live kitchen queue (FR-11): the given statuses oldest-confirmation-first, with their lines
     * and menu items fetched in the same query. Polled every ~2.5 s per connected tablet (NFR-02),
     * so the N+1 that a lazy collection would cause here is worth one join.
     *
     * <p>{@code confirmedAt} is non-null for every status the kitchen sees — it is stamped by
     * {@link Order#confirm()}, which is the only way into the queue — so the ordering is total with
     * no nulls to sort. Served by {@code idx_orders_status_confirmed}.
     */
    @Query("""
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.menuItem
            where o.status in :statuses
            order by o.confirmedAt asc
            """)
    List<Order> findQueueWithItems(Collection<OrderStatus> statuses);

    /**
     * Orders in a status, <em>longest-standing first</em> — the cashier's worklist of SERVED orders
     * awaiting settlement (FR-13). Deliberately the opposite ordering to the {@code createdAt DESC}
     * finders below: those answer "what happened recently?", this one answers "who has been waiting
     * to pay the longest?", which is the order a cashier works in.
     *
     * <p>{@code servedAt} is non-null for every SERVED order — it is stamped by
     * {@link Order#markServed()}, the only way into the status — so the ordering is total.
     */
    List<Order> findByStatusOrderByServedAtAsc(OrderStatus status);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<Order> findByTableIdOrderByCreatedAtDesc(Long tableId);

    List<Order> findByStatusAndTableIdOrderByCreatedAtDesc(OrderStatus status, Long tableId);

    /**
     * True if a menu item appears on any order. Lets {@code MenuService.deleteItem} refuse with a
     * descriptive 409 instead of letting the {@code fk_order_item_menu_item} RESTRICT surface as
     * a 500.
     */
    @Query("select count(i) > 0 from OrderItem i where i.menuItem.id = :menuItemId")
    boolean isMenuItemOrdered(Long menuItemId);
}

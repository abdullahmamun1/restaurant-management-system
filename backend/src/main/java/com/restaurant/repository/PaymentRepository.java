package com.restaurant.repository;

import com.restaurant.domain.Payment;
import com.restaurant.repository.projection.CategorySalesProjection;
import com.restaurant.repository.projection.SalesTotalsProjection;
import com.restaurant.repository.projection.TopItemProjection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Persistence access for {@link Payment} (Repository pattern).
 *
 * <p>Read and insert only, and — since M8 — <strong>structurally so</strong>: this extends
 * {@link Repository} rather than {@code JpaRepository} precisely so that {@code delete} and
 * {@code deleteAll} are not on the interface. "No refunds, cancellations or modifications after
 * payment" is a stated non-goal, and an interface that offers no way to do it is a stronger
 * statement of that than a convention nobody has broken yet. Same reasoning as
 * {@link InventoryAdjustmentRepository}; {@link Payment} is setter-free, so {@code save} on an
 * already-managed instance has nothing to write back either.
 *
 * <p><strong>M7's three report aggregates are all rooted here, deliberately</strong> (FR-23, FR-24).
 * A sales report is a question about <em>payments</em>, and rooting the queries at {@link Payment}
 * means an unpaid order is structurally incapable of appearing in revenue. Rooting them at
 * {@code Order} and filtering {@code status = PAID} would give the same answer today and would be
 * one careless {@code where} edit away from counting money that was never taken.
 *
 * <p>They return interface projections rather than entities: these scans touch every line of every
 * paid order in the range, and materialising the aggregate for that would be waste. Every date
 * filter is the half-open interval from a single {@code DateRange} (M7 D2/D5).
 */
public interface PaymentRepository extends Repository<Payment, Long> {

    /** Records a settlement. The only write this table has. */
    Payment save(Payment payment);

    /** The payment for an order, used to reprint a receipt (FR-16). At most one exists. */
    Optional<Payment> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    // ---- Reporting aggregates (M7: FR-23, FR-24) ---------------------------

    /**
     * The headline totals for a date range (FR-23), read from the frozen snapshot and never
     * recomputed from the configured rates (M7 D1).
     *
     * <p>{@code coalesce(..., 0)} is not defensive habit: {@code sum} over zero rows returns
     * {@code null} in SQL, and a quiet Tuesday would otherwise hand the client a null where it
     * expects an amount. An empty range is a well-formed zero (M7 D9).
     */
    @Query("""
            select coalesce(sum(p.grandTotal), 0)    as totalRevenue,
                   count(p)                          as ordersCompleted,
                   coalesce(sum(p.subtotal), 0)      as itemRevenue,
                   coalesce(sum(p.taxAmount), 0)     as taxTotal,
                   coalesce(sum(p.serviceCharge), 0) as serviceChargeTotal
            from Payment p
            where p.paidAt >= :from and p.paidAt < :toExclusive
            """)
    SalesTotalsProjection findSalesTotals(OffsetDateTime from, OffsetDateTime toExclusive);

    /**
     * FR-23's breakdown by menu category. Line revenue only — a category has no share of the
     * order-level tax or service charge (M7 D3), so these sum to {@code itemRevenue} above.
     *
     * <p>{@code i.unitPrice} is the snapshot taken when the line was added (M4), not
     * {@code m.price}: using the live menu price would make a past report move whenever a manager
     * edits the menu, which is the same failure as D1 one level down.
     *
     * <p>The category is resolved <em>live</em> through {@code menu_item.category_id} (M7 D4), so
     * re-filing an item moves its history with it. The totals are unaffected; only the attribution
     * shifts. Every historic line does resolve, because {@code MenuService} refuses to delete an
     * ordered item and the category FK is {@code ON DELETE RESTRICT}.
     */
    @Query("""
            select c.id                          as categoryId,
                   c.name                        as categoryName,
                   sum(i.unitPrice * i.quantity) as revenue,
                   sum(i.quantity)               as quantitySold
            from Payment p
              join p.order o
              join o.items i
              join i.menuItem m
              join m.category c
            where p.paidAt >= :from and p.paidAt < :toExclusive
            group by c.id, c.name
            order by sum(i.unitPrice * i.quantity) desc, c.name asc
            """)
    List<CategorySalesProjection> findCategoryBreakdown(OffsetDateTime from,
                                                        OffsetDateTime toExclusive);

    /**
     * FR-24's top sellers, ranked by quantity sold in the range.
     *
     * <p>The {@code c.name asc} tiebreak is not cosmetic. Postgres promises no ordering among equal
     * sort keys, so without it two items that each sold 12 can swap places between two identical
     * requests — and a manager who refreshes and sees a different number three will not trust the
     * screen again. The sort lives in the query rather than in the {@link Pageable} so a caller
     * cannot drop it by passing an unsorted page request; the {@code Pageable} is used purely as a
     * {@code LIMIT}.
     */
    @Query("""
            select m.id                          as menuItemId,
                   m.name                        as name,
                   c.name                        as categoryName,
                   sum(i.quantity)               as quantitySold,
                   sum(i.unitPrice * i.quantity) as revenue
            from Payment p
              join p.order o
              join o.items i
              join i.menuItem m
              join m.category c
            where p.paidAt >= :from and p.paidAt < :toExclusive
            group by m.id, m.name, c.name
            order by sum(i.quantity) desc, m.name asc
            """)
    List<TopItemProjection> findTopItems(OffsetDateTime from, OffsetDateTime toExclusive,
                                         Pageable pageable);
}

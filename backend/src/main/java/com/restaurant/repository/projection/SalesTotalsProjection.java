package com.restaurant.repository.projection;

import java.math.BigDecimal;

/**
 * The headline figures of a sales report (FR-23), aggregated straight from the {@code payment}
 * snapshot.
 *
 * <p>A Spring Data <strong>interface projection</strong>: no entity is materialised (these queries
 * touch every paid order in the range), and no controller DTO leaks down into the repository layer —
 * the service maps this to {@code SalesSummaryDto}, as everywhere else (NFR-05).
 *
 * <p><strong>Every figure here is read, never recomputed</strong> (M7 D1). The tax and
 * service-charge rates live in configuration and can change; a report that recomputed tax from
 * today's rate over last month's orders would let a config edit silently rewrite history. These are
 * the amounts that were actually charged.
 *
 * <p>{@code itemRevenue} is the sum of the payments' subtotals — the figure the FR-23 category
 * breakdown sums to. The gap up to {@code totalRevenue} is exactly
 * {@code taxTotal + serviceChargeTotal}: order-level charges that no category owns (M7 D3).
 */
public interface SalesTotalsProjection {

    /** Sum of {@code payment.grand_total} — FR-23's "total revenue". */
    BigDecimal getTotalRevenue();

    /** Count of payments — FR-23's "number of orders completed". One payment per order (V6). */
    long getOrdersCompleted();

    /** Sum of {@code payment.subtotal} — what the category breakdown adds up to. */
    BigDecimal getItemRevenue();

    BigDecimal getTaxTotal();

    BigDecimal getServiceChargeTotal();
}

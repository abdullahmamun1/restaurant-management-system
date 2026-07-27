package com.restaurant.repository.projection;

import java.math.BigDecimal;

/**
 * One category's contribution to a sales report (FR-23's "breakdown by menu category").
 *
 * <p><strong>Line revenue only</strong> (M7 D3). Tax and service charge are computed on the
 * <em>order's</em> subtotal and are order-level facts — there is no recorded figure saying how much
 * tax belonged to "Beverages", and apportioning it pro-rata would invent a number that reconciles to
 * nothing in the database. So these sum to {@code SalesTotalsProjection.getItemRevenue()}, not to
 * the grand total, and the DTO and the dashboard both say so.
 *
 * <p>Revenue comes from {@code order_item.unit_price}, the snapshot taken when the line was added
 * (M4) — never from the live {@code menu_item.price}, which would make a past report move when a
 * manager edits the menu.
 */
public interface CategorySalesProjection {

    Long getCategoryId();

    String getCategoryName();

    /** Sum of {@code unit_price * quantity} across the category's lines in paid orders in range. */
    BigDecimal getRevenue();

    long getQuantitySold();
}

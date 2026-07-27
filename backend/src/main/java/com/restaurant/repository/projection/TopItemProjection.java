package com.restaurant.repository.projection;

import java.math.BigDecimal;

/**
 * One row of the top-selling items report (FR-24), ranked by {@link #getQuantitySold()}.
 *
 * <p>{@code revenue} is not something FR-24 asks for — it asks only for the ranking by quantity —
 * but the aggregate query already groups by menu item, so the sum costs nothing and turns a bare
 * league table into something a manager can act on. Noted as an addition rather than smuggled in.
 *
 * <p>Like the category breakdown, revenue comes from the snapshotted {@code order_item.unit_price}.
 */
public interface TopItemProjection {

    Long getMenuItemId();

    String getName();

    String getCategoryName();

    /** Sum of {@code order_item.quantity} across paid orders in range — FR-24's ranking key. */
    long getQuantitySold();

    BigDecimal getRevenue();
}

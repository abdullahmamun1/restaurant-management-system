package com.restaurant.controller.dto;

import java.math.BigDecimal;

/**
 * One category's line in the FR-23 sales breakdown.
 *
 * <p>{@code revenue} is <strong>line revenue only</strong> — the sum of {@code unit_price ×
 * quantity} for that category's lines in the range's paid orders. It does not include any share of
 * tax or service charge, because those are computed on the order's subtotal and no category owns a
 * portion of them (M7 D3). The breakdown therefore sums to {@link SalesSummaryDto#itemRevenue()},
 * not to {@link SalesSummaryDto#totalRevenue()}.
 *
 * <p>{@code shareOfItemRevenue} is a percentage (1dp) of {@code itemRevenue}, computed server-side
 * like every other derived value in this API ({@code OrderDto.subtotal},
 * {@code KitchenTicketDto.waitingSeconds}, …). The dashboard's bar widths read it directly rather
 * than dividing two amounts in a template.
 */
public record CategorySalesDto(
        Long categoryId,
        String categoryName,
        BigDecimal revenue,
        long quantitySold,
        BigDecimal shareOfItemRevenue) {
}

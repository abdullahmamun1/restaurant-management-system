package com.restaurant.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The sales summary for a date range (FR-23): total revenue, orders completed, and the breakdown by
 * menu category.
 *
 * <p><strong>Two different totals, and the difference is the point.</strong> The category breakdown
 * sums to {@code itemRevenue}; the gap up to {@code totalRevenue} is exactly {@code taxTotal +
 * serviceChargeTotal}, order-level charges that no category owns (M7 D3). Both are returned so the
 * identity
 * <pre>itemRevenue + taxTotal + serviceChargeTotal == totalRevenue</pre>
 * holds exactly and the dashboard can <em>show</em> it — because the first manager to add up the
 * bars and find they fall short of the headline figure will otherwise file a bug against a correct
 * system.
 *
 * <p>Every amount is read from the frozen {@code payment} snapshot, never recomputed from the
 * configured rates (M7 D1), so a report of a past range does not move when the rates change.
 *
 * <p>{@code from}, {@code to} and {@code zone} are echoed back deliberately: the client sent local
 * dates and the server resolved them against a timezone the client does not know, so the response
 * states what was actually measured. That costs nothing and makes a printed dashboard
 * self-describing.
 */
public record SalesSummaryDto(
        LocalDate from,
        LocalDate to,
        String zone,
        BigDecimal totalRevenue,
        long ordersCompleted,
        BigDecimal itemRevenue,
        BigDecimal taxTotal,
        BigDecimal serviceChargeTotal,
        List<CategorySalesDto> categoryBreakdown) {
}

package com.restaurant.service;

import com.restaurant.controller.dto.CategorySalesDto;
import com.restaurant.controller.dto.SalesSummaryDto;
import com.restaurant.controller.dto.TopItemDto;
import com.restaurant.domain.DateRange;
import com.restaurant.repository.projection.CategorySalesProjection;
import com.restaurant.repository.projection.SalesTotalsProjection;
import com.restaurant.repository.projection.TopItemProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps the reporting projections to their DTOs (DTO + Mapper pattern), so repository types never
 * cross the REST boundary (NFR-05).
 *
 * <p>It carries the two derived values the client must not compute itself, following this
 * codebase's standing rule ({@code OrderDto.subtotal}, {@code IngredientDto.lowStock},
 * {@code KitchenTicketDto.waitingSeconds}, …):
 *
 * <ul>
 *   <li><strong>Scale.</strong> Every money figure is normalised to 2dp. {@code coalesce(sum(...),
 *       0)} comes back at scale 0 on an empty range, and a dashboard showing {@code 0} beside
 *       {@code 1240.50} looks broken. HALF_UP, matching {@code Order.subtotal()} and each
 *       {@code BillCharge}.</li>
 *   <li><strong>Share.</strong> {@code shareOfItemRevenue} is a percentage of {@code itemRevenue},
 *       not of {@code totalRevenue} (M7 D3) — the bars must add to 100%, and only the item revenue
 *       is attributable to categories. Zero item revenue yields {@code 0.0}, not a divide-by-zero.
 *       </li>
 * </ul>
 */
@Component
public class ReportingMapper {

    private static final int MONEY_SCALE = 2;
    /** Percentages to 1dp: enough to distinguish bars, not enough to imply false precision. */
    private static final int SHARE_SCALE = 1;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public SalesSummaryDto toSummary(DateRange range,
                                     SalesTotalsProjection totals,
                                     List<CategorySalesProjection> breakdown) {
        BigDecimal itemRevenue = money(totals.getItemRevenue());

        return new SalesSummaryDto(
                range.from(),
                range.to(),
                range.zone().getId(),
                money(totals.getTotalRevenue()),
                totals.getOrdersCompleted(),
                itemRevenue,
                money(totals.getTaxTotal()),
                money(totals.getServiceChargeTotal()),
                breakdown.stream().map(row -> toCategory(row, itemRevenue)).toList());
    }

    public List<TopItemDto> toTopItems(List<TopItemProjection> rows) {
        return rows.stream()
                .map(row -> new TopItemDto(
                        row.getMenuItemId(),
                        row.getName(),
                        row.getCategoryName(),
                        row.getQuantitySold(),
                        money(row.getRevenue())))
                .toList();
    }

    private CategorySalesDto toCategory(CategorySalesProjection row, BigDecimal itemRevenue) {
        BigDecimal revenue = money(row.getRevenue());
        return new CategorySalesDto(
                row.getCategoryId(),
                row.getCategoryName(),
                revenue,
                row.getQuantitySold(),
                share(revenue, itemRevenue));
    }

    /** Null-safe (an aggregate over no rows) and always at 2dp, so the client only formats. */
    private BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * {@code part} as a percentage of {@code whole}, to 1dp. A zero whole means there was nothing to
     * take a share of, which is 0.0 — not an {@link ArithmeticException} on the dashboard.
     */
    private BigDecimal share(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(SHARE_SCALE, RoundingMode.HALF_UP);
        }
        return part.multiply(HUNDRED).divide(whole, SHARE_SCALE, RoundingMode.HALF_UP);
    }
}

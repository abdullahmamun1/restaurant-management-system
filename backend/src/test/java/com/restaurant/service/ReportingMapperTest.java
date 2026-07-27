package com.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.controller.dto.CategorySalesDto;
import com.restaurant.controller.dto.SalesSummaryDto;
import com.restaurant.controller.dto.TopItemDto;
import com.restaurant.domain.DateRange;
import com.restaurant.repository.projection.CategorySalesProjection;
import com.restaurant.repository.projection.SalesTotalsProjection;
import com.restaurant.repository.projection.TopItemProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the reporting projection &rarr; DTO mapping. Pure — the projections are hand-built
 * stubs, so no Spring and no database.
 *
 * <p>The two properties under test are the ones the client depends on and cannot check for itself:
 * every money figure arrives at 2dp (so the dashboard only formats, never rounds), and the category
 * shares are taken against <em>item</em> revenue so the bars add to 100% (M7 D3).
 */
class ReportingMapperTest {

    private final ReportingMapper mapper = new ReportingMapper();

    private static final DateRange RANGE = new DateRange(
            LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-27"), ZoneId.of("Asia/Dhaka"));

    // ---- stubs -------------------------------------------------------------

    private SalesTotalsProjection totals(String total, long orders, String items,
                                         String tax, String service) {
        return new SalesTotalsProjection() {
            public BigDecimal getTotalRevenue() {
                return total == null ? null : new BigDecimal(total);
            }

            public long getOrdersCompleted() {
                return orders;
            }

            public BigDecimal getItemRevenue() {
                return items == null ? null : new BigDecimal(items);
            }

            public BigDecimal getTaxTotal() {
                return tax == null ? null : new BigDecimal(tax);
            }

            public BigDecimal getServiceChargeTotal() {
                return service == null ? null : new BigDecimal(service);
            }
        };
    }

    private CategorySalesProjection category(long id, String name, String revenue, long qty) {
        return new CategorySalesProjection() {
            public Long getCategoryId() {
                return id;
            }

            public String getCategoryName() {
                return name;
            }

            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }

            public long getQuantitySold() {
                return qty;
            }
        };
    }

    private TopItemProjection topItem(long id, String name, String category, long qty, String revenue) {
        return new TopItemProjection() {
            public Long getMenuItemId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public String getCategoryName() {
                return category;
            }

            public long getQuantitySold() {
                return qty;
            }

            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }
        };
    }

    // ---- tests -------------------------------------------------------------

    @Test
    @DisplayName("the range and zone are echoed back so the response states what was measured")
    void echoesTheResolvedRange() {
        SalesSummaryDto dto = mapper.toSummary(
                RANGE, totals("115.00", 1, "100.00", "5.00", "10.00"), List.of());

        assertThat(dto.from()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(dto.to()).isEqualTo(LocalDate.parse("2026-07-27"));
        assertThat(dto.zone()).isEqualTo("Asia/Dhaka");
    }

    @Test
    @DisplayName("the D3 identity holds: items + tax + service == total")
    void theTotalsReconcile() {
        SalesSummaryDto dto = mapper.toSummary(
                RANGE, totals("1380.00", 12, "1200.00", "60.00", "120.00"), List.of());

        assertThat(dto.itemRevenue().add(dto.taxTotal()).add(dto.serviceChargeTotal()))
                .isEqualByComparingTo(dto.totalRevenue());
        assertThat(dto.ordersCompleted()).isEqualTo(12);
    }

    @Test
    @DisplayName("every money field comes out at 2dp, including a scale-0 zero from an empty range")
    void moneyIsAlwaysScaledToCents() {
        // What `coalesce(sum(...), 0)` actually returns when no payments fall in the range.
        SalesSummaryDto dto = mapper.toSummary(RANGE, totals("0", 0, "0", "0", "0"), List.of());

        assertThat(dto.totalRevenue().scale()).isEqualTo(2);
        assertThat(dto.itemRevenue().scale()).isEqualTo(2);
        assertThat(dto.taxTotal().scale()).isEqualTo(2);
        assertThat(dto.serviceChargeTotal().scale()).isEqualTo(2);
        assertThat(dto.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(dto.categoryBreakdown()).isEmpty();
    }

    @Test
    @DisplayName("a null aggregate (no rows at all) maps to 0.00 rather than propagating null")
    void nullAggregatesBecomeZero() {
        SalesSummaryDto dto = mapper.toSummary(RANGE, totals(null, 0, null, null, null), List.of());

        assertThat(dto.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(dto.itemRevenue()).isEqualByComparingTo("0.00");
        assertThat(dto.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(dto.serviceChargeTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("category shares are taken against item revenue and sum to 100%")
    void sharesAreOfItemRevenueAndSumToOneHundred() {
        SalesSummaryDto dto = mapper.toSummary(
                RANGE,
                totals("1150.00", 8, "1000.00", "50.00", "100.00"),
                List.of(category(1, "Mains", "750.00", 30),
                        category(2, "Beverages", "250.00", 50)));

        // 750/1000 and 250/1000 — NOT of the 1150.00 grand total, which would give 65.2 / 21.7.
        assertThat(dto.categoryBreakdown()).extracting(CategorySalesDto::shareOfItemRevenue)
                .containsExactly(new BigDecimal("75.0"), new BigDecimal("25.0"));

        BigDecimal totalShare = dto.categoryBreakdown().stream()
                .map(CategorySalesDto::shareOfItemRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalShare).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("the breakdown sums to item revenue, not to the grand total")
    void breakdownSumsToItemRevenue() {
        SalesSummaryDto dto = mapper.toSummary(
                RANGE,
                totals("1150.00", 8, "1000.00", "50.00", "100.00"),
                List.of(category(1, "Mains", "750.00", 30),
                        category(2, "Beverages", "250.00", 50)));

        BigDecimal sum = dto.categoryBreakdown().stream()
                .map(CategorySalesDto::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo(dto.itemRevenue());
        assertThat(sum).isNotEqualByComparingTo(dto.totalRevenue());
    }

    @Test
    @DisplayName("zero item revenue yields a 0.0 share rather than dividing by zero")
    void zeroItemRevenueDoesNotDivideByZero() {
        SalesSummaryDto dto = mapper.toSummary(
                RANGE, totals("0", 0, "0", "0", "0"), List.of(category(1, "Mains", "0.00", 0)));

        assertThat(dto.categoryBreakdown()).singleElement()
                .extracting(CategorySalesDto::shareOfItemRevenue)
                .isEqualTo(new BigDecimal("0.0"));
    }

    @Test
    @DisplayName("top items keep the query's ranking and carry 2dp revenue")
    void topItemsPreserveOrderAndScale() {
        List<TopItemDto> rows = mapper.toTopItems(List.of(
                topItem(7, "Beef Burger", "Mains", 42, "12600.5"),
                topItem(3, "Iced Tea", "Beverages", 31, "3100")));

        assertThat(rows).extracting(TopItemDto::name).containsExactly("Beef Burger", "Iced Tea");
        assertThat(rows).extracting(TopItemDto::quantitySold).containsExactly(42L, 31L);
        assertThat(rows).allSatisfy(row -> assertThat(row.revenue().scale()).isEqualTo(2));
        assertThat(rows.get(0).revenue()).isEqualByComparingTo("12600.50");
    }

    @Test
    @DisplayName("an empty top-items result maps to an empty list, not null")
    void emptyTopItemsIsAnEmptyList() {
        assertThat(mapper.toTopItems(List.of())).isEmpty();
    }
}

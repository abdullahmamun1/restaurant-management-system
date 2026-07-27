package com.restaurant.service.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.config.BillingProperties;
import com.restaurant.domain.Bill;
import com.restaurant.domain.BillLine;
import com.restaurant.domain.MenuCategory;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.Order;
import com.restaurant.domain.RestaurantTable;
import com.restaurant.domain.Role;
import com.restaurant.domain.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the FR-14 bill computation: subtotal &rarr; each charge &rarr; grand total. Pure —
 * the calculator and its charge strategies are wired by hand, with no Spring context and no
 * database.
 *
 * <p>The property worth pinning hardest is that <em>the printed lines sum exactly to the printed
 * total</em>. It holds because each charge rounds itself to 2dp rather than the total being rounded
 * once at the end, and it is the difference between a correct receipt and one that is a cent out.
 */
class BillCalculatorTest {

    /** The configured defaults: 5% tax, 10% service charge. */
    private BillCalculator calculator(String taxRate, String serviceRate) {
        BillingProperties properties =
                new BillingProperties(new BigDecimal(taxRate), new BigDecimal(serviceRate));
        return new BillCalculator(List.of(new TaxCharge(properties), new ServiceCharge(properties)));
    }

    private BillCalculator calculator() {
        return calculator("0.05", "0.10");
    }

    private Order orderOf(String... unitPrices) {
        Order order = new Order(new RestaurantTable("T1", 4),
                new User("cashier", "hash", Role.CASHIER, "Test Cashier"));
        int index = 0;
        for (String price : unitPrices) {
            order.addItem(new MenuItem("Item " + index++, "desc", new BigDecimal(price),
                    new MenuCategory("Mains", 0), true), 1, null);
        }
        return order;
    }

    @Test
    @DisplayName("subtotal, tax and service charge are computed on the subtotal and named separately")
    void computesTheBreakdown() {
        Bill bill = calculator().compute(orderOf("100.00"));

        assertThat(bill.subtotal()).isEqualByComparingTo("100.00");
        assertThat(bill.charges()).extracting(BillLine::label)
                .containsExactly(TaxCharge.LABEL, ServiceCharge.LABEL);
        assertThat(bill.amountOf(TaxCharge.LABEL)).isEqualByComparingTo("5.00");
        assertThat(bill.amountOf(ServiceCharge.LABEL)).isEqualByComparingTo("10.00");
        assertThat(bill.grandTotal()).isEqualByComparingTo("115.00");
    }

    @Test
    @DisplayName("charges apply to the subtotal and do not compound with each other")
    void chargesDoNotCompound() {
        Bill bill = calculator().compute(orderOf("200.00"));

        // Compounding would make the service charge 10% of 210.00 = 21.00 and the total 231.00.
        assertThat(bill.amountOf(ServiceCharge.LABEL)).isEqualByComparingTo("20.00");
        assertThat(bill.grandTotal()).isEqualByComparingTo("230.00");
    }

    @Test
    @DisplayName("the printed charge lines sum exactly to the printed grand total")
    void linesSumToTheTotal() {
        // 13.33 * 5% = 0.6665 and * 10% = 1.333 — both need rounding, in opposite directions.
        Bill bill = calculator().compute(orderOf("13.33"));

        BigDecimal sum = bill.charges().stream()
                .map(BillLine::amount)
                .reduce(bill.subtotal(), BigDecimal::add);

        assertThat(bill.amountOf(TaxCharge.LABEL)).isEqualByComparingTo("0.67"); // HALF_UP
        assertThat(bill.amountOf(ServiceCharge.LABEL)).isEqualByComparingTo("1.33");
        assertThat(bill.grandTotal()).isEqualByComparingTo(sum);
        assertThat(bill.grandTotal()).isEqualByComparingTo("15.33");
    }

    @Test
    @DisplayName("every charge is stated to 2dp so the bill never prints a fraction of a cent")
    void chargesAreScaledToCents() {
        Bill bill = calculator().compute(orderOf("13.33"));

        assertThat(bill.charges()).allSatisfy(line -> assertThat(line.amount().scale()).isEqualTo(2));
    }

    @Test
    @DisplayName("zero rates make the grand total the subtotal")
    void zeroRatesLeaveTheSubtotal() {
        Bill bill = calculator("0", "0").compute(orderOf("42.50"));

        assertThat(bill.grandTotal()).isEqualByComparingTo(bill.subtotal());
        assertThat(bill.grandTotal()).isEqualByComparingTo("42.50");
    }

    @Test
    @DisplayName("an order with no items bills as 0.00 rather than failing")
    void emptyOrderBillsAsZero() {
        Bill bill = calculator().compute(orderOf());

        assertThat(bill.subtotal()).isEqualByComparingTo("0.00");
        assertThat(bill.grandTotal()).isEqualByComparingTo("0.00");
        assertThat(bill.charges()).extracting(BillLine::amount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("a charge that does not apply reads as 0.00 rather than null")
    void unknownChargeIsZero() {
        Bill bill = new BillCalculator(List.of()).compute(orderOf("50.00"));

        assertThat(bill.amountOf(TaxCharge.LABEL)).isEqualByComparingTo("0.00");
        assertThat(bill.grandTotal()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("a negative configured rate fails at startup, not at the till")
    void negativeRateIsRejected() {
        assertThatThrownBy(() -> new BillingProperties(new BigDecimal("-0.05"), BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tax-rate");
    }
}

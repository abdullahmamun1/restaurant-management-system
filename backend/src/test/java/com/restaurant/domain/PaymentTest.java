package com.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Payment}, the snapshot the whole billing design rests on: an unpaid bill is
 * recomputed on every read, a paid one is <em>this row</em>, read back unchanged however long
 * afterwards and whatever the configured rates have since become.
 *
 * <p>The absence of mutators is the enforcement of "no refunds, cancellations or modifications
 * after payment", so it is asserted here rather than left to be noticed.
 */
class PaymentTest {

    private Payment payment() {
        Order order = new Order(new RestaurantTable("T3", 4),
                new User("waiter", "hash", Role.WAITER, "Test Waiter"));
        User cashier = new User("cashier", "hash", Role.CASHIER, "Test Cashier");
        return new Payment(order, cashier, PaymentMethod.CARD,
                new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("10.00"),
                new BigDecimal("115.00"), new BigDecimal("0.0500"), new BigDecimal("0.1000"));
    }

    @Test
    @DisplayName("a payment's amounts are exactly what it was constructed with")
    void snapshotsItsAmounts() {
        Payment payment = payment();

        assertThat(payment.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(payment.getTaxAmount()).isEqualByComparingTo("5.00");
        assertThat(payment.getServiceCharge()).isEqualByComparingTo("10.00");
        assertThat(payment.getGrandTotal()).isEqualByComparingTo("115.00");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    @DisplayName("the rates in force are frozen alongside the amounts they produced")
    void snapshotsTheRates() {
        Payment payment = payment();

        assertThat(payment.getTaxRate()).isEqualByComparingTo("0.05");
        assertThat(payment.getServiceChargeRate()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("the parts sum to the grand total, as the DB CHECK also insists")
    void partsSumToTheTotal() {
        Payment payment = payment();

        assertThat(payment.getSubtotal()
                .add(payment.getTaxAmount())
                .add(payment.getServiceCharge()))
                .isEqualByComparingTo(payment.getGrandTotal());
    }

    @Test
    @DisplayName("paidAt is stamped on construction, so the receipt can carry it without a re-read")
    void stampsPaidAt() {
        assertThat(payment().getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("a payment exposes no mutator — there is no path to a refund or an edit")
    void isImmutable() {
        assertThat(Arrays.stream(Payment.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("set")))
                .isEmpty();
    }
}

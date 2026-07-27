package com.restaurant.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.InsufficientStockException;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.PaymentMethod;
import com.restaurant.service.BillingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * NFR-04: payment recording and inventory deduction are one transaction — all of it, or none.
 *
 * <p>"If any step fails, the entire transaction shall be rolled back to prevent partial updates."
 * The failure worth testing is the realistic one: stock has fallen since the order was validated,
 * because FR-08 deliberately does not reserve (M4 D1). The deduction throws, and everything the
 * settlement had already done — the payment row, the order's move to PAID, the table's release —
 * has to come back with it.
 *
 * <p>The assertion that actually catches a broken transaction boundary is the <em>second</em>
 * ingredient: one the deduction loop would have reached <strong>before</strong> the one that fails.
 * If the boundary were wrong, that ingredient would be left deducted while no payment exists — a
 * partial update, which is precisely what NFR-04 forbids. Checking only the failing ingredient
 * would miss it entirely.
 */
@SpringBootTest
@Tag("integration")
class PaymentRollbackIT {

    @Autowired private BillingService billing;
    @Autowired private SettlementFixture fixture;

    @AfterEach
    void cleanUp() {
        fixture.tearDown();
    }

    @Test
    @DisplayName("a deduction that would go negative rolls back the whole settlement")
    void shortfallRollsBackEverything() {
        String suffix = "roll" + System.nanoTime();

        // Two ingredients on one dish. Ingredients are locked in ascending id order (M6 D2), and
        // `plenty` is created first, so the deduction loop reaches and deducts it BEFORE it hits
        // the one that throws — which is what makes the rollback assertion below meaningful.
        Ingredient plenty = fixture.ingredient(suffix + "-plenty", "1000.000");
        Ingredient scarce = fixture.ingredient(suffix + "-scarce", "1000.000");
        MenuItem dish = fixture.dish(suffix, "12.00", plenty, "50.000");
        fixture.addRecipeLine(dish, scarce, "50.000");

        // Both are well stocked while the order is built, so FR-08 lets the item on.
        Long orderId = fixture.servedOrder(suffix, dish, 1);

        // ...and then stock falls before the bill is settled. This is the real scenario, and the
        // unavoidable consequence of FR-08 not reserving: another table took it, or a manager
        // wrote it off. Reaching this state by adding an under-stocked item is impossible — the
        // validator chain refuses that at add time.
        fixture.forceStock(scarce.getId(), "10.000");

        BigDecimal plentyBefore = fixture.stockOf(plenty.getId());
        BigDecimal scarceBefore = fixture.stockOf(scarce.getId());

        assertThatThrownBy(() -> billing.pay(orderId, PaymentMethod.CASH, "cashier"))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(fixture.paymentCountFor(orderId))
                .as("no money may be recorded when the deduction failed")
                .isZero();
        assertThat(fixture.statusOf(orderId))
                .as("the order stays SERVED so it can be settled again after a restock")
                .isEqualTo("SERVED");
        assertThat(fixture.stockOf(scarce.getId()))
                .as("the failing ingredient is untouched")
                .isEqualByComparingTo(scarceBefore);
        assertThat(fixture.stockOf(plenty.getId()))
                .as("THE ONE THAT MATTERS: an ingredient the loop already deducted must be "
                        + "restored, or the transaction boundary is broken (NFR-04)")
                .isEqualByComparingTo(plentyBefore);
    }

    @Test
    @DisplayName("a successful settlement commits all of it together")
    void successCommitsEverything() {
        String suffix = "ok" + System.nanoTime();
        Ingredient ingredient = fixture.ingredient(suffix, "500.000");
        MenuItem dish = fixture.dish(suffix, "12.00", ingredient, "50.000");
        Long orderId = fixture.servedOrder(suffix, dish, 2);

        billing.pay(orderId, PaymentMethod.CASH, "cashier");

        assertThat(fixture.paymentCountFor(orderId)).isEqualTo(1);
        assertThat(fixture.statusOf(orderId)).isEqualTo("PAID");
        assertThat(fixture.stockOf(ingredient.getId()))
                .as("2 portions x 50g deducted")
                .isEqualByComparingTo("400.000");
        assertThat(fixture.tableStatusOf(orderId))
                .as("the table is released on payment (FR-15b)")
                .isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("an order cannot be paid twice")
    void doublePaymentIsRefused() {
        String suffix = "twice" + System.nanoTime();
        Ingredient ingredient = fixture.ingredient(suffix, "500.000");
        MenuItem dish = fixture.dish(suffix, "12.00", ingredient, "50.000");
        Long orderId = fixture.servedOrder(suffix, dish, 1);

        billing.pay(orderId, PaymentMethod.CASH, "cashier");
        assertThatThrownBy(() -> billing.pay(orderId, PaymentMethod.CASH, "cashier"))
                .isInstanceOf(RuntimeException.class);

        assertThat(fixture.paymentCountFor(orderId))
                .as("a refused second payment must not add a row")
                .isEqualTo(1);
        assertThat(fixture.stockOf(ingredient.getId()))
                .as("nor deduct a second time")
                .isEqualByComparingTo("450.000");
    }
}

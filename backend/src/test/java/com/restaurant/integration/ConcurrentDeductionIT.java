package com.restaurant.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.PaymentMethod;
import com.restaurant.service.BillingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * NFR-07: concurrent inventory deductions for the same ingredient are serialized.
 *
 * <p>This is M6's shell probe promoted to a backend test, as the roadmap asked. It is worth having
 * twice over, because the bug it originally caught was invisible to code review:
 * {@code findByIdForUpdate} locked the row, but the ingredient was already in the persistence
 * context (loaded eagerly through {@code RecipeItem.ingredient} while computing the draw), so
 * Hibernate handed back the instance holding its <em>pre-lock</em> stock. Two payments both deducted
 * from the same stale figure and one deduction silently vanished. The fix — {@code EntityManager
 * .refresh} inside the lock — is only observable under real contention, so this test is the only
 * thing standing between that regression and production.
 *
 * <p>Repeated, because a race that passes once has not been tested.
 */
@SpringBootTest
@Tag("integration")
class ConcurrentDeductionIT {

    @Autowired private BillingService billing;
    @Autowired private SettlementFixture fixture;

    @AfterEach
    void cleanUp() {
        fixture.tearDown();
    }

    @RepeatedTest(5)
    @DisplayName("two payments racing for the last portion: exactly one wins, stock never negative")
    void contendedDeductionSerializes() throws Exception {
        String suffix = "conc" + System.nanoTime();
        // Enough for exactly one of the two orders.
        Ingredient scarce = fixture.ingredient(suffix, "100.000");
        MenuItem dish = fixture.dish(suffix, "10.00", scarce, "100.000");

        Long orderA = fixture.servedOrder(suffix + "a", dish, 1);
        Long orderB = fixture.servedOrder(suffix + "b", dish, 1);

        CyclicBarrier together = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> payA = () -> {
                together.await(10, TimeUnit.SECONDS);
                try {
                    billing.pay(orderA, PaymentMethod.CASH, "cashier");
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };
            Callable<Boolean> payB = () -> {
                together.await(10, TimeUnit.SECONDS);
                try {
                    billing.pay(orderB, PaymentMethod.CASH, "cashier");
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };

            List<Future<Boolean>> results = pool.invokeAll(List.of(payA, payB));
            long won = 0;
            for (Future<Boolean> r : results) {
                if (r.get(60, TimeUnit.SECONDS)) {
                    won++;
                }
            }

            assertThat(won)
                    .as("only one of two payments can consume the last 100g")
                    .isEqualTo(1);

            BigDecimal remaining = fixture.stockOf(scarce.getId());
            assertThat(remaining)
                    .as("stock must never go below zero (FR-22)")
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(remaining)
                    .as("exactly one draw of 100g must have been applied — not zero, not two")
                    .isEqualByComparingTo("0.000");

            assertThat(fixture.paymentCountFor(orderA) + fixture.paymentCountFor(orderB))
                    .as("exactly one payment row")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("payments on disjoint ingredients still run in parallel — no over-locking")
    void disjointDeductionsBothSucceed() throws Exception {
        String suffix = "disj" + System.nanoTime();
        Ingredient one = fixture.ingredient(suffix + "1", "500.000");
        Ingredient two = fixture.ingredient(suffix + "2", "500.000");
        MenuItem dishOne = fixture.dish(suffix + "1", "10.00", one, "100.000");
        MenuItem dishTwo = fixture.dish(suffix + "2", "10.00", two, "100.000");

        Long orderA = fixture.servedOrder(suffix + "a", dishOne, 1);
        Long orderB = fixture.servedOrder(suffix + "b", dishTwo, 1);

        CyclicBarrier together = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> payA = () -> {
                together.await(10, TimeUnit.SECONDS);
                billing.pay(orderA, PaymentMethod.CASH, "cashier");
                return true;
            };
            Callable<Boolean> payB = () -> {
                together.await(10, TimeUnit.SECONDS);
                billing.pay(orderB, PaymentMethod.CASH, "cashier");
                return true;
            };

            for (Future<Boolean> r : pool.invokeAll(List.of(payA, payB))) {
                assertThat(r.get(60, TimeUnit.SECONDS))
                        .as("locking one ingredient must not block a payment on another")
                        .isTrue();
            }

            assertThat(fixture.stockOf(one.getId())).isEqualByComparingTo("400.000");
            assertThat(fixture.stockOf(two.getId())).isEqualByComparingTo("400.000");
        } finally {
            pool.shutdownNow();
        }
    }
}

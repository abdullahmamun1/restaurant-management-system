package com.restaurant.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.domain.RestaurantTable;
import com.restaurant.repository.RestaurantTableRepository;
import com.restaurant.service.OrderService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NFR-07 / FR-06: two waiters cannot open an order on the same table at the same instant
 * (M8 D10).
 *
 * <p><strong>Why this test exists.</strong> M4 built two protections for this race —
 * {@code RestaurantTableRepository.findByIdForUpdate} and the {@code uq_active_order_per_table}
 * partial unique index — and neither was ever fired at. M6's ingredient probe, the last untested
 * concurrency claim in this project, turned out to be covering a genuine lost update. Two
 * protections and zero evidence is exactly the shape of that earlier bug.
 *
 * <p>Repeated, because a race that passes once has not been tested. Each round uses its own table
 * so the rounds cannot interfere with one another.
 */
@SpringBootTest
@Tag("integration")
class TableClaimRaceIT {

    @Autowired private OrderService orderService;
    @Autowired private RestaurantTableRepository tables;
    @Autowired private DataSource dataSource;
    @Autowired private TransactionTemplate tx;

    private JdbcTemplate jdbc;
    private Long tableId;

    @BeforeEach
    void seed() {
        jdbc = new JdbcTemplate(dataSource);
        String label = "IT" + (System.nanoTime() % 100000);
        tx.executeWithoutResult(status ->
                tableId = tables.save(new RestaurantTable(label, 2)).getId());
    }

    @AfterEach
    void cleanUp() {
        if (tableId != null) {
            jdbc.update("DELETE FROM order_item WHERE order_id IN "
                    + "(SELECT id FROM orders WHERE table_id = ?)", tableId);
            jdbc.update("DELETE FROM orders WHERE table_id = ?", tableId);
            jdbc.update("DELETE FROM restaurant_table WHERE id = ?", tableId);
        }
    }

    @RepeatedTest(5)
    @DisplayName("two simultaneous claims on one table produce exactly one order")
    void onlyOneWaiterClaimsTheTable() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        // Both threads wait here, so they hit createOrder as close to simultaneously as the JVM
        // allows. Without the barrier one reliably finishes before the other starts and the test
        // proves nothing.
        CyclicBarrier startTogether = new CyclicBarrier(2);

        Callable<Outcome> claim = () -> {
            startTogether.await(10, TimeUnit.SECONDS);
            try {
                orderService.createOrder(tableId, "waiter");
                return new Outcome(true, null);
            } catch (Exception e) {
                return new Outcome(false, e.getClass().getSimpleName());
            }
        };

        try {
            List<Future<Outcome>> futures = pool.invokeAll(List.of(claim, claim));
            long succeeded = 0;
            for (Future<Outcome> f : futures) {
                if (f.get(30, TimeUnit.SECONDS).won()) {
                    succeeded++;
                }
            }

            assertThat(succeeded)
                    .as("exactly one waiter may claim a free table (FR-06)")
                    .isEqualTo(1);

            Integer orders = jdbc.queryForObject(
                    "SELECT count(*) FROM orders WHERE table_id = ? AND status <> 'PAID'",
                    Integer.class, tableId);
            assertThat(orders)
                    .as("the table must carry exactly one open order, never two")
                    .isEqualTo(1);

            String status = jdbc.queryForObject(
                    "SELECT status FROM restaurant_table WHERE id = ?", String.class, tableId);
            assertThat(status).isEqualTo("OCCUPIED");
        } finally {
            pool.shutdownNow();
            // Each round starts from a clean table.
            jdbc.update("DELETE FROM orders WHERE table_id = ?", tableId);
            jdbc.update("UPDATE restaurant_table SET status = 'AVAILABLE' WHERE id = ?", tableId);
        }
    }

    private record Outcome(boolean won, String failure) {}
}

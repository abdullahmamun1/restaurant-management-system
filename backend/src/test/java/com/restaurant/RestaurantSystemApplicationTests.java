package com.restaurant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies the Spring application context loads.
 *
 * <p>Note: this test starts the full context and therefore needs a reachable database
 * (see docker-compose.yml). It is a plain context-load check for M0; slice/unit tests
 * that don't require a DB will be added alongside the features they cover.
 */
@SpringBootTest
class RestaurantSystemApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: fails if the context cannot start.
    }
}

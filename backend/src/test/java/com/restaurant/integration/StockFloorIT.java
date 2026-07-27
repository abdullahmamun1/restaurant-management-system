package com.restaurant.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.InsufficientStockException;
import com.restaurant.repository.IngredientRepository;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-22 / NFR-04: ingredient stock can never go below zero — <strong>both</strong> halves.
 *
 * <p>The SRS asks for this to hold for "any inventory deduction — automated or manual", so it is
 * guarded twice: {@code Ingredient.applyAdjustment} in the domain, and the
 * {@code ingredient_stock_nonneg} CHECK constraint in the schema. A unit test can only reach the
 * first. This reaches the second by going around the entity with raw SQL, which is the only way to
 * show the constraint is actually on the table rather than merely written in a migration file
 * somebody assumed had run.
 */
@SpringBootTest
@Tag("integration")
class StockFloorIT {

    @Autowired private IngredientRepository ingredients;
    @Autowired private DataSource dataSource;
    @Autowired private TransactionTemplate tx;

    private JdbcTemplate jdbc;
    private Long ingredientId;

    private final String fixtureName = "IT Floor " + System.nanoTime();

    @BeforeEach
    void seed() {
        jdbc = new JdbcTemplate(dataSource);
        tx.executeWithoutResult(status -> ingredientId = ingredients.save(new Ingredient(
                fixtureName, "g", new BigDecimal("100.000"), new BigDecimal("10.000"))).getId());
    }

    @AfterEach
    void cleanUp() {
        if (ingredientId != null) {
            jdbc.update("DELETE FROM ingredient WHERE id = ?", ingredientId);
        }
    }

    @Test
    @DisplayName("the domain refuses a deduction that would go negative (FR-22)")
    void domainGuardHolds() {
        tx.executeWithoutResult(status -> {
            Ingredient ingredient = ingredients.findById(ingredientId).orElseThrow();

            assertThatThrownBy(() -> ingredient.applyAdjustment(new BigDecimal("-100.001")))
                    .isInstanceOf(InsufficientStockException.class);

            assertThat(ingredient.getStockQty())
                    .as("a refused adjustment must not have moved the stock")
                    .isEqualByComparingTo("100.000");
        });
    }

    @Test
    @DisplayName("deducting down to exactly zero is allowed — the floor is zero, not one")
    void exactlyZeroIsAllowed() {
        tx.executeWithoutResult(status -> {
            Ingredient ingredient = ingredients.findById(ingredientId).orElseThrow();
            assertThat(ingredient.applyAdjustment(new BigDecimal("-100.000")))
                    .isEqualByComparingTo("0.000");
        });

        BigDecimal stored = jdbc.queryForObject(
                "SELECT stock_qty FROM ingredient WHERE id = ?", BigDecimal.class, ingredientId);
        assertThat(stored).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("the CHECK constraint refuses a negative write that bypasses the domain")
    void schemaGuardHolds() {
        // Straight past Ingredient.applyAdjustment: this is what a stray migration or a console
        // session would do, and it is the case the domain guard cannot cover.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE ingredient SET stock_qty = -1 WHERE id = ?", ingredientId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ingredient_stock_nonneg");

        BigDecimal stored = jdbc.queryForObject(
                "SELECT stock_qty FROM ingredient WHERE id = ?", BigDecimal.class, ingredientId);
        assertThat(stored).isEqualByComparingTo("100.000");
    }
}

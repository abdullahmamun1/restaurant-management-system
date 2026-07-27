package com.restaurant.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.InventoryAdjustment;
import com.restaurant.domain.Role;
import com.restaurant.domain.User;
import com.restaurant.repository.IngredientRepository;
import com.restaurant.repository.InventoryAdjustmentRepository;
import com.restaurant.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NFR-06: the FR-21 audit log is append-only <em>at the database</em>, not merely by convention
 * (M8 D2).
 *
 * <p>This is the half that matters. The application half — a repository that does not declare
 * {@code delete} — is verified by the fact that the code compiles. What this test proves is the
 * part that holds against callers the interface does not control: a migration, a console session,
 * a future mapping mistake. So it goes around JPA entirely and issues raw SQL.
 */
@SpringBootTest
@Tag("integration")
class AuditImmutabilityIT {

    @Autowired private IngredientRepository ingredients;
    @Autowired private InventoryAdjustmentRepository adjustments;
    @Autowired private UserRepository users;
    @Autowired private DataSource dataSource;
    @Autowired private TransactionTemplate tx;
    @Autowired private EntityManager em;

    private JdbcTemplate jdbc;
    private Long ingredientId;
    private Long adjustmentId;

    /** Unique per run: these tests share the development database with everything else. */
    private final String fixtureName = "IT Audit " + System.nanoTime();

    @BeforeEach
    void seed() {
        jdbc = new JdbcTemplate(dataSource);
        User manager = users.findByUsername("manager").orElseThrow();

        tx.executeWithoutResult(status -> {
            Ingredient ingredient = ingredients.save(new Ingredient(
                    fixtureName, "g", new BigDecimal("100.000"), new BigDecimal("10.000")));
            InventoryAdjustment adjustment = adjustments.save(new InventoryAdjustment(
                    ingredient, manager, new BigDecimal("5.000"), "IT seed",
                    new BigDecimal("105.000")));
            em.flush();
            ingredientId = ingredient.getId();
            adjustmentId = adjustment.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        // The trigger blocks DELETE, so tearing the fixture down needs it disabled for this
        // statement. Doing it here rather than weakening the trigger keeps production behaviour
        // absolute, which is the point of it.
        if (adjustmentId != null) {
            jdbc.execute("ALTER TABLE inventory_adjustment DISABLE TRIGGER "
                    + "trg_inventory_adjustment_append_only");
            try {
                jdbc.update("DELETE FROM inventory_adjustment WHERE id = ?", adjustmentId);
            } finally {
                jdbc.execute("ALTER TABLE inventory_adjustment ENABLE TRIGGER "
                        + "trg_inventory_adjustment_append_only");
            }
        }
        if (ingredientId != null) {
            jdbc.update("DELETE FROM ingredient WHERE id = ?", ingredientId);
        }
    }

    @Test
    @DisplayName("an audit row cannot be UPDATEd, even by raw SQL")
    void updateIsRefused() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE inventory_adjustment SET reason = 'tampered' WHERE id = ?", adjustmentId))
                .hasMessageContaining("append-only");

        String reason = jdbc.queryForObject(
                "SELECT reason FROM inventory_adjustment WHERE id = ?", String.class, adjustmentId);
        assertThat(reason).isEqualTo("IT seed");
    }

    @Test
    @DisplayName("an audit row cannot be DELETEd, even by raw SQL")
    void deleteIsRefused() {
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM inventory_adjustment WHERE id = ?", adjustmentId))
                .hasMessageContaining("append-only");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM inventory_adjustment WHERE id = ?", Integer.class,
                adjustmentId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("a blanket DELETE cannot wipe the log")
    void bulkDeleteIsRefused() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_adjustment"))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("appending still works — the log is append-only, not read-only")
    void insertStillWorks() {
        Integer before = jdbc.queryForObject(
                "SELECT count(*) FROM inventory_adjustment WHERE ingredient_id = ?",
                Integer.class, ingredientId);
        assertThat(before).isEqualTo(1);
        assertThat(adjustments.findByIngredientIdOrderByCreatedAtDesc(ingredientId)).hasSize(1);
    }
}

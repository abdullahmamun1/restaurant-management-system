package com.restaurant.integration;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.MenuCategory;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.RecipeItem;
import com.restaurant.domain.RestaurantTable;
import com.restaurant.repository.IngredientRepository;
import com.restaurant.repository.MenuCategoryRepository;
import com.restaurant.repository.MenuItemRepository;
import com.restaurant.repository.RecipeItemRepository;
import com.restaurant.repository.RestaurantTableRepository;
import com.restaurant.service.OrderService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Builds and tears down the fixtures the settlement integration tests need: an ingredient, a dish
 * with a recipe drawing on it, a table, and an order walked all the way to SERVED.
 *
 * <p><strong>Fixture discipline is not optional here.</strong> These tests run against the same
 * Neon database the application uses, so every row is created with a run-unique name and removed
 * in teardown even when a test fails. Nothing assumes a seeded row exists, and nothing is left
 * behind — otherwise the next run inherits a floor with no free tables, which is exactly what the
 * M4–M7 verification runs kept doing.
 */
@Component
public class SettlementFixture {

    private final MenuCategoryRepository categories;
    private final MenuItemRepository menuItems;
    private final IngredientRepository ingredients;
    private final RecipeItemRepository recipes;
    private final RestaurantTableRepository tables;
    private final OrderService orderService;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;

    private final List<Long> createdOrders = new ArrayList<>();
    private final List<Long> createdTables = new ArrayList<>();
    private final List<Long> createdMenuItems = new ArrayList<>();
    private final List<Long> createdIngredients = new ArrayList<>();
    private final List<Long> createdCategories = new ArrayList<>();

    public SettlementFixture(MenuCategoryRepository categories,
                             MenuItemRepository menuItems,
                             IngredientRepository ingredients,
                             RecipeItemRepository recipes,
                             RestaurantTableRepository tables,
                             OrderService orderService,
                             TransactionTemplate tx,
                             DataSource dataSource) {
        this.categories = categories;
        this.menuItems = menuItems;
        this.ingredients = ingredients;
        this.recipes = recipes;
        this.tables = tables;
        this.orderService = orderService;
        this.tx = tx;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** An ingredient with exactly {@code stock} on hand. */
    public Ingredient ingredient(String suffix, String stock) {
        return tx.execute(status -> {
            Ingredient saved = ingredients.save(new Ingredient(
                    "IT Ing " + suffix, "g", new BigDecimal(stock), BigDecimal.ZERO));
            createdIngredients.add(saved.getId());
            return saved;
        });
    }

    /** A dish whose recipe draws {@code draw} of {@code ingredient} per portion. */
    public MenuItem dish(String suffix, String price, Ingredient ingredient, String draw) {
        return tx.execute(status -> {
            MenuCategory category = categories.save(new MenuCategory("IT Cat " + suffix, 900));
            createdCategories.add(category.getId());
            MenuItem item = menuItems.save(new MenuItem(
                    "IT Dish " + suffix, "integration fixture", new BigDecimal(price),
                    category, true));
            createdMenuItems.add(item.getId());
            recipes.save(new RecipeItem(item, ingredient, new BigDecimal(draw)));
            return item;
        });
    }

    /** Adds a second ingredient to an existing dish's recipe. */
    public void addRecipeLine(MenuItem dish, Ingredient ingredient, String draw) {
        tx.executeWithoutResult(status ->
                recipes.save(new RecipeItem(dish, ingredient, new BigDecimal(draw))));
    }

    /** Walks a fresh table through create → add → confirm → prepare → ready → serve. */
    public Long servedOrder(String suffix, MenuItem dish, int quantity) {
        Long tableId = tx.execute(status -> {
            Long id = tables.save(new RestaurantTable(
                    "I" + (System.nanoTime() % 100000), 2)).getId();
            createdTables.add(id);
            return id;
        });

        Long orderId = orderService.createOrder(tableId, "waiter").id();
        createdOrders.add(orderId);
        orderService.addItem(orderId, dish.getId(), quantity, null);
        orderService.confirm(orderId);
        orderService.markPreparing(orderId);
        orderService.markReady(orderId);
        orderService.markServed(orderId);
        return orderId;
    }

    /**
     * Forces an ingredient's stock, bypassing the domain guard.
     *
     * <p>Models what actually happens between validation and settlement: FR-08 deliberately does
     * not <em>reserve</em> stock (M4 D1), so another order or a manager's adjustment can take it
     * in between. Tests cannot get there by adding an under-stocked item, because the FR-08 chain
     * refuses that up front — which is the system working, and is why this exists.
     */
    public void forceStock(Long ingredientId, String qty) {
        jdbc.update("UPDATE ingredient SET stock_qty = ? WHERE id = ?",
                new BigDecimal(qty), ingredientId);
    }

    /** Current stock straight from the table, bypassing any persistence context. */
    public BigDecimal stockOf(Long ingredientId) {
        return jdbc.queryForObject(
                "SELECT stock_qty FROM ingredient WHERE id = ?", BigDecimal.class, ingredientId);
    }

    public int paymentCountFor(Long orderId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM payment WHERE order_id = ?", Integer.class, orderId);
        return n == null ? 0 : n;
    }

    public String statusOf(Long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    /** The status of the table an order sits on — how FR-15(b)'s release is observed. */
    public String tableStatusOf(Long orderId) {
        return jdbc.queryForObject(
                "SELECT t.status FROM restaurant_table t "
                        + "JOIN orders o ON o.table_id = t.id WHERE o.id = ?",
                String.class, orderId);
    }

    /** Removes everything this fixture created, innermost foreign key first. */
    public void tearDown() {
        createdOrders.forEach(id -> {
            jdbc.update("DELETE FROM payment WHERE order_id = ?", id);
            jdbc.update("DELETE FROM order_item WHERE order_id = ?", id);
            jdbc.update("DELETE FROM orders WHERE id = ?", id);
        });
        createdTables.forEach(id -> jdbc.update("DELETE FROM restaurant_table WHERE id = ?", id));
        createdMenuItems.forEach(id -> {
            jdbc.update("DELETE FROM recipe_item WHERE menu_item_id = ?", id);
            jdbc.update("DELETE FROM menu_item WHERE id = ?", id);
        });
        createdCategories.forEach(id -> jdbc.update("DELETE FROM menu_category WHERE id = ?", id));
        createdIngredients.forEach(id -> jdbc.update("DELETE FROM ingredient WHERE id = ?", id));

        createdOrders.clear();
        createdTables.clear();
        createdMenuItems.clear();
        createdCategories.clear();
        createdIngredients.clear();
    }
}

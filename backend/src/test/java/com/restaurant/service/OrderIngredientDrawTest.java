package com.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.restaurant.domain.Ingredient;
import com.restaurant.domain.MenuCategory;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.Order;
import com.restaurant.domain.RecipeItem;
import com.restaurant.domain.RestaurantTable;
import com.restaurant.domain.Role;
import com.restaurant.domain.User;
import com.restaurant.repository.RecipeItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the shared ingredient-draw calculation (FR-08's check and FR-19's deduction both
 * run through it). The repository is stubbed; there is no Spring context and no database.
 *
 * <p>This is the function whose correctness both the stock check and the payment deduction rest on,
 * so the cases here are the ones where the two could otherwise have drifted apart: aggregation
 * across lines that share an ingredient, multiplication by line quantity, and the silence of a menu
 * item that has no recipe.
 */
class OrderIngredientDrawTest {

    private final RecipeItemRepository recipes = mock(RecipeItemRepository.class);
    private final OrderIngredientDraw draw = new OrderIngredientDraw(recipes);

    private final MenuCategory mains = new MenuCategory("Mains", 0);

    /** Entity ids are DB-assigned; the draw keys on them, so tests set them directly. */
    private <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private MenuItem menuItem(String name, long id) {
        return withId(new MenuItem(name, "desc", new BigDecimal("10.00"), mains, true), id);
    }

    private Ingredient ingredient(String name, long id) {
        return withId(new Ingredient(name, "g", new BigDecimal("1000"), BigDecimal.ZERO), id);
    }

    private Order orderWith(MenuItem item, int quantity) {
        Order order = new Order(new RestaurantTable("T1", 4),
                new User("waiter", "hash", Role.WAITER, "Test Waiter"));
        order.addItem(item, quantity, null);
        return order;
    }

    @Test
    @DisplayName("a line's draw is its recipe quantity times the line quantity")
    void multipliesByLineQuantity() {
        MenuItem steak = menuItem("Steak", 1L);
        Ingredient beef = ingredient("Beef", 10L);
        when(recipes.findByMenuItemIdIn(any())).thenReturn(
                List.of(new RecipeItem(steak, beef, new BigDecimal("250"))));

        Map<Long, BigDecimal> result = draw.forOrder(orderWith(steak, 3));

        assertThat(result).hasSize(1);
        assertThat(result.get(10L)).isEqualByComparingTo("750");
    }

    @Test
    @DisplayName("two menu items sharing an ingredient aggregate into one entry")
    void aggregatesAcrossLinesSharingAnIngredient() {
        MenuItem burger = menuItem("Burger", 1L);
        MenuItem meatballs = menuItem("Meatballs", 2L);
        Ingredient beef = ingredient("Beef", 10L);
        Ingredient bun = ingredient("Bun", 11L);
        when(recipes.findByMenuItemIdIn(any())).thenReturn(List.of(
                new RecipeItem(burger, beef, new BigDecimal("150")),
                new RecipeItem(burger, bun, new BigDecimal("1")),
                new RecipeItem(meatballs, beef, new BigDecimal("100"))));

        Order order = orderWith(burger, 2);
        order.addItem(meatballs, 3, null);

        Map<Long, BigDecimal> result = draw.forOrder(order);

        assertThat(result.get(10L)).isEqualByComparingTo("600"); // 2*150 + 3*100
        assertThat(result.get(11L)).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("the same menu item on two lines (different notes) is summed before the recipe applies")
    void sumsRepeatedMenuItemAcrossLines() {
        MenuItem steak = menuItem("Steak", 1L);
        Ingredient beef = ingredient("Beef", 10L);
        when(recipes.findByMenuItemIdIn(any())).thenReturn(
                List.of(new RecipeItem(steak, beef, new BigDecimal("250"))));

        // Distinct notes keep these as separate lines (see OrderItem.matches).
        Order order = orderWith(steak, 1);
        order.addItem(steak, 2, "rare");

        assertThat(draw.forOrder(order).get(10L)).isEqualByComparingTo("750");
    }

    @Test
    @DisplayName("a menu item with no recipe contributes nothing — recipes are optional")
    void itemWithoutRecipeContributesNothing() {
        MenuItem water = menuItem("Water", 5L);
        when(recipes.findByMenuItemIdIn(any())).thenReturn(List.of());

        assertThat(draw.forOrder(orderWith(water, 4))).isEmpty();
    }

    @Test
    @DisplayName("an empty order draws nothing without querying for recipes")
    void emptyOrderSkipsTheQuery() {
        Order empty = new Order(new RestaurantTable("T1", 4),
                new User("waiter", "hash", Role.WAITER, "Test Waiter"));

        assertThat(draw.forOrder(empty)).isEmpty();
        verifyNoInteractions(recipes);
    }
}

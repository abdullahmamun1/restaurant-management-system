package com.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the {@link Order} aggregate's guards and totals (FR-07, FR-09, FR-10, FR-12,
 * FR-13's subtotal). Pure domain — no Spring context and no database.
 *
 * <p>The kitchen transition guards are what M5's {@code /prepare} and {@code /ready} endpoints rest
 * on: those endpoints add no rules of their own, so the rules are pinned here.
 */
class OrderTest {

    private static final BigDecimal PRICE = new BigDecimal("12.50");

    private Order pendingOrder() {
        return new Order(new RestaurantTable("T1", 4),
                new User("waiter", "hash", Role.WAITER, "Test Waiter"));
    }

    private MenuItem menuItem(String name, String price) {
        return new MenuItem(name, "desc", new BigDecimal(price),
                new MenuCategory("Mains", 0), true);
    }

    /** Entity ids are DB-assigned; tests that need one set it directly. */
    private void assignId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    /** A one-line order sitting in the kitchen queue (FR-11). */
    private Order confirmedOrder() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 1, null);
        order.confirm();
        return order;
    }

    private Order readyOrder() {
        Order order = confirmedOrder();
        order.markPreparing();
        order.markReady();
        return order;
    }

    // ---- Item edits (FR-07, FR-09) -----------------------------------------

    @Test
    @DisplayName("a new order starts PENDING, empty and editable")
    void startsPendingAndEmpty() {
        Order order = pendingOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.subtotal()).isEqualByComparingTo("0.00");
        assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("creating an order occupies its table (FR-06)")
    void tableIsOccupiedSeparately() {
        RestaurantTable table = new RestaurantTable("T2", 2);
        table.occupy();
        assertThat(table.getStatus()).isEqualTo(TableStatus.OCCUPIED);
        assertThat(table.isAvailable()).isFalse();
        assertThatThrownBy(table::occupy)
                .isInstanceOf(TableUnavailableException.class)
                .hasMessageContaining("T2");
    }

    @Test
    @DisplayName("the same item with the same notes merges into one line instead of duplicating")
    void repeatedItemMergesQuantities() {
        Order order = pendingOrder();
        MenuItem steak = menuItem("Steak", "12.50");

        order.addItem(steak, 2, "rare");
        order.addItem(steak, 1, "rare");

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("blank and absent notes are the same line; different notes stay separate")
    void notesDecideLineIdentity() {
        Order order = pendingOrder();
        MenuItem steak = menuItem("Steak", "12.50");

        order.addItem(steak, 1, null);
        order.addItem(steak, 1, "   ");      // normalizes to null → merges
        order.addItem(steak, 1, "well done"); // distinct → new line

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(order.getItems().get(0).getNotes()).isNull();
        assertThat(order.getItems().get(1).getNotes()).isEqualTo("well done");
    }

    @Test
    @DisplayName("a line snapshots the price at add time, so later menu edits do not move it")
    void unitPriceIsSnapshotted() {
        Order order = pendingOrder();
        MenuItem steak = menuItem("Steak", "12.50");
        order.addItem(steak, 2, null);

        steak.update("Steak", "desc", new BigDecimal("99.00"),
                new MenuCategory("Mains", 0), true);

        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo(PRICE);
        assertThat(order.subtotal()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("removing a line while PENDING succeeds; an unknown line reports not-found")
    void removeItemWhilePending() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 1, null);
        assignId(order.getItems().get(0), 7L);

        assertThat(order.removeItem(99L)).isFalse();
        assertThat(order.removeItem(7L)).isTrue();
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    @DisplayName("changing a line's quantity while PENDING re-prices the line, not the item")
    void changeItemQuantityWhilePending() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 2, null);
        assignId(order.getItems().get(0), 7L);

        assertThat(order.changeItemQuantity(7L, 5)).isPresent();
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(5);
        // The snapshotted unit price is untouched, so the line total follows the quantity only.
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo(PRICE);
        assertThat(order.subtotal()).isEqualByComparingTo("62.50");

        assertThat(order.changeItemQuantity(7L, 1)).isPresent();
        assertThat(order.subtotal()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("changing the quantity of a line that is not on the order reports not-found")
    void changeItemQuantityUnknownLine() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 1, null);
        assignId(order.getItems().get(0), 7L);

        assertThat(order.changeItemQuantity(99L, 3)).isEmpty();
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("quantity zero is refused — removing a line is a different operation")
    void changeItemQuantityRejectsZero() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 2, null);
        assignId(order.getItems().get(0), 7L);

        assertThatThrownBy(() -> order.changeItemQuantity(7L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
        assertThatThrownBy(() -> order.changeItemQuantity(7L, -3))
                .isInstanceOf(IllegalArgumentException.class);

        // The refusal leaves the line exactly as it was.
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("quantity cannot be changed once the order is CONFIRMED (FR-09)")
    void changeItemQuantityLockedAfterConfirm() {
        Order order = confirmedOrder();
        Long lineId = 7L;
        assignId(order.getItems().get(0), lineId);

        assertThatThrownBy(() -> order.changeItemQuantity(lineId, 4))
                .isInstanceOf(IllegalOrderStateException.class);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("items are locked once the order is CONFIRMED (FR-09)")
    void itemsLockedAfterConfirm() {
        Order order = pendingOrder();
        MenuItem steak = menuItem("Steak", "12.50");
        order.addItem(steak, 1, null);
        assignId(order.getItems().get(0), 7L);
        order.confirm();

        assertThatThrownBy(() -> order.addItem(steak, 1, null))
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("PENDING");
        assertThatThrownBy(() -> order.removeItem(7L))
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("CONFIRMED");
        assertThat(order.getItems()).hasSize(1);
    }

    // ---- Lifecycle (FR-09, FR-10) ------------------------------------------

    @Test
    @DisplayName("an empty order cannot be sent to the kitchen")
    void emptyOrderCannotBeConfirmed() {
        Order order = pendingOrder();
        assertThatThrownBy(order::confirm)
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("no items");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("confirming stamps confirmedAt and closes the edit window (FR-09)")
    void confirmStampsTimestamp() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 1, null);
        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
        assertThat(order.getStatus().allowsItemEdits()).isFalse();
    }

    @Test
    @DisplayName("confirming twice is rejected — a double-tap is a conflict, not a no-op")
    void confirmIsNotIdempotent() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 1, null);
        order.confirm();

        assertThatThrownBy(order::confirm).isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    @DisplayName("SERVED is not reachable from CONFIRMED — the kitchen stages come first")
    void cannotServeBeforeReady() {
        Order order = confirmedOrder();

        assertThatThrownBy(order::markServed)
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("SERVED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getServedAt()).isNull();
    }

    @Test
    @DisplayName("SERVED from READY succeeds and stamps servedAt (FR-10)")
    void serveFromReady() {
        Order order = readyOrder();

        assertThatCode(order::markServed).doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SERVED);
        assertThat(order.getServedAt()).isNotNull();
    }

    // ---- Kitchen transitions (FR-12) ---------------------------------------

    @Test
    @DisplayName("the kitchen cannot start a PENDING order — it is not in the queue yet")
    void cannotPrepareBeforeConfirm() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 1, null);

        assertThatThrownBy(order::markPreparing)
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("PREPARING");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("CONFIRMED → PREPARING → READY is the kitchen's path (FR-12)")
    void kitchenAdvancesTheTicket() {
        Order order = confirmedOrder();

        assertThatCode(order::markPreparing).doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);

        assertThatCode(order::markReady).doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
    }

    @Test
    @DisplayName("two kitchen staff starting the same ticket: the second is a conflict, not a no-op")
    void startingTwiceIsRejected() {
        Order order = confirmedOrder();
        order.markPreparing();

        assertThatThrownBy(order::markPreparing)
                .isInstanceOf(IllegalOrderStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("cooking cannot be skipped — READY is not reachable from CONFIRMED")
    void cannotSkipPreparing() {
        Order order = confirmedOrder();

        assertThatThrownBy(order::markReady)
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("READY");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a READY ticket cannot go back to PREPARING — the lifecycle is one-way")
    void cannotGoBackwards() {
        Order order = readyOrder();

        assertThatThrownBy(order::markPreparing)
                .isInstanceOf(IllegalOrderStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
    }

    @Test
    @DisplayName("nothing is permitted after payment — PAID is final")
    void paidIsFinal() {
        Order order = readyOrder();
        order.markServed();
        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThatThrownBy(order::markServed).isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(() -> order.addItem(menuItem("Fries", "4.00"), 1, null))
                .isInstanceOf(IllegalOrderStateException.class);
    }

    // ---- Totals -------------------------------------------------------------

    @Test
    @DisplayName("subtotal sums line totals at snapshotted prices, scaled to 2dp")
    void subtotalSumsLines() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 2, null);   // 25.00
        order.addItem(menuItem("Fries", "4.25"), 3, null);    // 12.75
        order.addItem(menuItem("Soda", "1.99"), 1, null);     //  1.99

        assertThat(order.subtotal()).isEqualByComparingTo("39.74");
        assertThat(order.subtotal().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("the item list is not modifiable from outside the aggregate")
    void itemListIsUnmodifiable() {
        Order order = pendingOrder();
        order.addItem(menuItem("Steak", "12.50"), 1, null);

        assertThatThrownBy(() -> order.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

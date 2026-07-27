package com.restaurant.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A dine-in order for one table (FR-06–FR-10) and the <strong>aggregate root</strong> for its
 * {@link OrderItem} lines: lines are only created or removed through {@link #addItem} and
 * {@link #removeItem}, so the FR-07/FR-09 edit window cannot be bypassed.
 *
 * <p>Every lifecycle rule is enforced here rather than in the service or controller, and each one
 * defers to {@link OrderStatus} — the {@link OrderStatus State} enum owns the transition table
 * and {@code allowsItemEdits}, while this class owns the rules that need order data (an empty
 * order cannot be confirmed) and the timestamps. Maps to {@code orders}.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    /** The waiter who opened the order — recorded for audit and reporting, not for ownership. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "waiter_id", nullable = false)
    private User waiter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private final List<OrderItem> items = new ArrayList<>();

    /**
     * Set by the application rather than left to the column default (unlike the other entities)
     * because {@code OrderDto} returns it from the create call — a DB-side default would come back
     * null until the entity was re-read. The DB default remains as a backstop.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "served_at")
    private OffsetDateTime servedAt;

    protected Order() {
        // Required by JPA.
    }

    /** Opens a PENDING order and takes the table (FR-06). */
    public Order(RestaurantTable table, User waiter) {
        this.table = table;
        this.waiter = waiter;
        this.status = OrderStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    // ---- Item edits (FR-07, FR-08, FR-09) ----------------------------------

    /**
     * Adds {@code quantity} of {@code menuItem} to the order, merging into an existing line when
     * the item and notes match (a waiter tapping the same dish twice means "three of these", not
     * two identical lines). Permitted only while PENDING (FR-07).
     *
     * <p>Availability and ingredient-stock validation (FR-08) happens in the service's validator
     * chain <em>before</em> this is called, so a rejected item never mutates the order.
     *
     * @throws IllegalOrderStateException if items may no longer be edited (FR-09).
     */
    public OrderItem addItem(MenuItem menuItem, int quantity, String notes) {
        requireItemEdits("add items to");
        Optional<OrderItem> existing = items.stream()
                .filter(line -> line.matches(menuItem, notes))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().increaseQuantity(quantity);
            return existing.get();
        }
        OrderItem line = new OrderItem(this, menuItem, quantity, notes);
        items.add(line);
        return line;
    }

    /**
     * Removes a line by id. Permitted only while PENDING (FR-07); orphan removal deletes the row.
     *
     * @return {@code true} if a line was removed, {@code false} if no such line is on this order.
     * @throws IllegalOrderStateException if items may no longer be edited (FR-09).
     */
    public boolean removeItem(Long orderItemId) {
        requireItemEdits("remove items from");
        // orderItemId first: a line added earlier in this same transaction may not have an id yet.
        return items.removeIf(line -> orderItemId.equals(line.getId()));
    }

    /**
     * Sets an existing line's quantity outright. Permitted only while PENDING (FR-07), through the
     * same guard as {@link #addItem} and {@link #removeItem} — so the FR-09 lock holds however the
     * quantity is changed.
     *
     * <p>Deliberately <em>set</em> rather than increment: a waiter tapping "+" four times quickly
     * would otherwise send four increments whose order and arrival the server cannot rely on,
     * whereas four absolute values converge on the right answer whatever sequence they land in.
     *
     * <p>Availability and stock validation (FR-08) happens in the service before this is called,
     * and only when the quantity <em>rises</em> — reducing a line cannot make an order unfulfillable.
     *
     * @return the updated line, or empty if no such line is on this order.
     * @throws IllegalOrderStateException if items may no longer be edited (FR-09).
     * @throws IllegalArgumentException   if {@code newQuantity} is below 1 — that is a removal.
     */
    public Optional<OrderItem> changeItemQuantity(Long orderItemId, int newQuantity) {
        requireItemEdits("change quantities on");
        Optional<OrderItem> line = items.stream()
                .filter(item -> orderItemId.equals(item.getId()))
                .findFirst();
        line.ifPresent(item -> item.changeQuantity(newQuantity));
        return line;
    }

    // ---- Lifecycle transitions ---------------------------------------------

    /**
     * Confirms the order and sends it to the kitchen queue; items are locked from here on
     * (FR-09).
     *
     * @throws IllegalOrderStateException if the order is empty or not PENDING.
     */
    public void confirm() {
        if (items.isEmpty()) {
            throw new IllegalOrderStateException(
                    "Order " + id + " has no items — add at least one before confirming.");
        }
        transitionTo(OrderStatus.CONFIRMED);
        this.confirmedAt = OffsetDateTime.now();
    }

    /** Kitchen has started cooking (FR-12) — used from M5. */
    public void markPreparing() {
        transitionTo(OrderStatus.PREPARING);
    }

    /** Kitchen has finished; the order is ready to run (FR-12) — used from M5. */
    public void markReady() {
        transitionTo(OrderStatus.READY);
    }

    /** The waiter has delivered the food to the table (FR-10). Legal only from READY. */
    public void markServed() {
        transitionTo(OrderStatus.SERVED);
        this.servedAt = OffsetDateTime.now();
    }

    /** Settles the order (FR-15) — used from M6 inside the atomic payment transaction. */
    public void markPaid() {
        transitionTo(OrderStatus.PAID);
    }

    // ---- Totals -------------------------------------------------------------

    /**
     * Sum of the line totals at their snapshotted prices (FR-13's subtotal). M6's bill
     * computation builds tax and service charge on top of this rather than recomputing it.
     */
    public BigDecimal subtotal() {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ---- Guards -------------------------------------------------------------

    /** The single choke point for lifecycle changes — the State enum decides what is legal. */
    private void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalOrderStateException("Order " + id + " is " + status
                    + " and cannot move to " + target + ". Allowed from " + status + ": "
                    + (status.allowedTransitions().isEmpty() ? "nothing (final state)"
                            : status.allowedTransitions()) + ".");
        }
        this.status = target;
    }

    private void requireItemEdits(String attempted) {
        if (!status.allowsItemEdits()) {
            throw new IllegalOrderStateException("Order " + id + " is " + status
                    + "; you can only " + attempted + " an order while it is PENDING.");
        }
    }

    public Long getId() {
        return id;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public User getWaiter() {
        return waiter;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** Unmodifiable — lines are added and removed through {@link #addItem}/{@link #removeItem}. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public OffsetDateTime getServedAt() {
        return servedAt;
    }
}

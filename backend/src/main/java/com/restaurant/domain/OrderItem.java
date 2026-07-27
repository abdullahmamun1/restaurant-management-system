package com.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line on an order: a {@link MenuItem}, a quantity, and optional preparation notes (FR-07).
 *
 * <p>{@code unitPrice} is a <strong>snapshot</strong> of the menu price taken when the line was
 * added, and is immutable thereafter — a later price edit must not retroactively change an open
 * or settled order's total (needed for M6 billing and M7 revenue reporting to be truthful).
 *
 * <p>Lines are owned by the {@link Order} aggregate root and are only ever created or removed
 * through it, so the FR-07/FR-09 edit window is enforced in one place. Maps to {@code order_item}.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(length = 300)
    private String notes;

    protected OrderItem() {
        // Required by JPA.
    }

    OrderItem(Order order, MenuItem menuItem, int quantity, String notes) {
        this.order = order;
        this.menuItem = menuItem;
        this.quantity = quantity;
        this.unitPrice = menuItem.getPrice();
        this.notes = normalizeNotes(notes);
    }

    /** Line total at the snapshotted price. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Merges an additional quantity into this line — see {@link Order#addItem}. */
    void increaseQuantity(int extra) {
        this.quantity += extra;
    }

    /**
     * Sets this line's quantity outright — see {@link Order#changeItemQuantity}.
     *
     * <p>Refuses anything below 1: a line that exists must be for at least one of something.
     * "None of this dish" is a <em>removal</em>, which the aggregate already expresses through
     * {@link Order#removeItem}, and giving zero a second meaning here would leave two ways to
     * delete a line with only one of them going through that path.
     *
     * <p>The unit price is untouched, so changing a quantity never re-prices a line at today's
     * menu price — the snapshot taken when the line was added still stands.
     */
    void changeQuantity(int newQuantity) {
        if (newQuantity < 1) {
            throw new IllegalArgumentException(
                    "Quantity must be at least 1, but was " + newQuantity
                            + ". Remove the line instead.");
        }
        this.quantity = newQuantity;
    }

    /**
     * True when a new line for {@code menuItem} with these {@code notes} would duplicate this
     * one, in which case the quantities are merged rather than a second line created. Distinct
     * notes stay distinct lines — the kitchen needs to see them separately.
     */
    boolean matches(MenuItem candidate, String candidateNotes) {
        return sameMenuItem(candidate)
                && Objects.equals(notes, normalizeNotes(candidateNotes));
    }

    /**
     * Identity by reference first, then by id. Comparing ids alone would treat two <em>different</em>
     * unsaved menu items as the same line, since both ids are null.
     */
    private boolean sameMenuItem(MenuItem candidate) {
        if (menuItem == candidate) {
            return true;
        }
        Long mine = menuItem.getId();
        return mine != null && mine.equals(candidate.getId());
    }

    /** Blank and absent notes are the same thing; trimmed so whitespace never splits a line. */
    private static String normalizeNotes(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getNotes() {
        return notes;
    }
}

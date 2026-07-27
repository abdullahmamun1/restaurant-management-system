package com.restaurant.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The order lifecycle {@code PENDING → CONFIRMED → PREPARING → READY → SERVED → PAID} (SRS §2.4).
 *
 * <p><strong>Design pattern: State</strong> (enum-backed variant). A JPA entity's persisted field
 * <em>is</em> its status, so rather than a hierarchy of state classes this enum owns the
 * transition table and the per-state capability predicates, and {@link Order} delegates every
 * guard to it. The point of the pattern here is that no {@code if}/{@code switch} on status exists
 * anywhere in the service or controller layers: {@link #canTransitionTo} is the single authority
 * on what may follow what, and {@link #allowsItemEdits} the single authority on FR-07/FR-09.
 *
 * <p>The full table is declared even though M4 only exposes {@code confirm} (FR-09) and
 * {@code markServed} (FR-10); the kitchen transitions (M5, FR-12) and payment (M6, FR-15) then
 * add no lifecycle logic of their own and simply request a transition.
 */
public enum OrderStatus {

    /** Being built by the waiter — the only state in which items may be added or removed (FR-07). */
    PENDING,
    /** Sent to the kitchen queue; items are locked from here on (FR-09). */
    CONFIRMED,
    /** Kitchen has started cooking (FR-12). */
    PREPARING,
    /** Kitchen has finished; awaiting delivery to the table (FR-12). */
    READY,
    /** Delivered to the table (FR-10) — billable. */
    SERVED,
    /** Settled; stock has been deducted (FR-15, FR-19). Terminal — no refunds or edits. */
    PAID;

    /**
     * Legal successors per state. Populated in a static block because an enum constant's
     * constructor arguments may not reference other constants of the same enum.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(PENDING, EnumSet.of(CONFIRMED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(PREPARING));
        TRANSITIONS.put(PREPARING, EnumSet.of(READY));
        TRANSITIONS.put(READY, EnumSet.of(SERVED));
        TRANSITIONS.put(SERVED, EnumSet.of(PAID));
        TRANSITIONS.put(PAID, EnumSet.noneOf(OrderStatus.class));
    }

    /** True when {@code target} is a legal next state from this one. */
    public boolean canTransitionTo(OrderStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    /** The legal next states — useful for error messages and client affordances. */
    public Set<OrderStatus> allowedTransitions() {
        return Collections.unmodifiableSet(TRANSITIONS.get(this));
    }

    /**
     * Whether order items may be added or removed in this state. Only {@code PENDING} qualifies:
     * once the kitchen has the ticket the contents are fixed (FR-07, FR-09).
     */
    public boolean allowsItemEdits() {
        return this == PENDING;
    }

    /** True for a state with no successors — {@code PAID} only (no refunds or cancellations). */
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}

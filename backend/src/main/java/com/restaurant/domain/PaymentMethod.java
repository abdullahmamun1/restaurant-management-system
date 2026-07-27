package com.restaurant.domain;

/**
 * How the money arrived (SRS glossary; FR-15).
 *
 * <p><strong>Deliberately a plain enum, not a Strategy.</strong> CLAUDE.md's pattern table
 * proposes Strategy here, and it was considered and rejected: this system has no payment gateway,
 * and FR-15 asks only that the method be <em>recorded</em>. Three strategy classes with identical
 * bodies would be exactly the decoration the same document warns against. The things that would
 * genuinely differentiate them — change due on cash, a reference number on card or mobile — are
 * not in the SRS, and inventing them to justify a pattern is scope creep. See
 * {@code docs/design-patterns.md}, "deliberately not used".
 */
public enum PaymentMethod {
    CASH,
    CARD,
    MOBILE
}

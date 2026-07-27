package com.restaurant.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * What a served order costs (FR-14): the subtotal, each named charge, and the grand total.
 *
 * <p><strong>A computed value object, not an entity — there is no {@code bill} table.</strong> The
 * glossary defines a Bill as "a financial document <em>generated from</em> a SERVED Order", and
 * because {@code OrderItem.unitPrice} is already an immutable snapshot, an unpaid bill is fully
 * derivable from the order. A persisted bill would be a second source of truth for the same
 * numbers. Once money moves the amounts <em>are</em> frozen — on the {@link Payment} row, because
 * the tax and service-charge rates that produced them live in configuration and may change.
 * The split is clean: <strong>unpaid &rarr; compute; paid &rarr; read the snapshot.</strong>
 */
public record Bill(BigDecimal subtotal, List<BillLine> charges, BigDecimal grandTotal) {

    public Bill {
        charges = List.copyOf(charges);
    }

    /**
     * The amount of the charge with this label, or {@code 0.00} if no such charge applies.
     *
     * <p>Used where the SRS names a specific charge — the {@code payment} row and the receipt both
     * enumerate tax and service charge as their own fields (FR-16), so those two have to be
     * addressable individually rather than as an anonymous list. A charge bean beyond those two
     * would show up on the bill and the total but would need a schema change to be persisted
     * separately; that is the honest limit of the Strategy's openness here.
     */
    public BigDecimal amountOf(String label) {
        return charges.stream()
                .filter(line -> line.label().equals(label))
                .map(BillLine::amount)
                .findFirst()
                .orElseGet(() -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}

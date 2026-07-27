package com.restaurant.service.billing;

import com.restaurant.domain.Bill;
import com.restaurant.domain.BillLine;
import com.restaurant.domain.Order;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Computes what a served order costs (FR-14).
 *
 * <p><strong>Design pattern: Template Method</strong> for the fixed skeleton — subtotal, then each
 * charge, then the grand total — combined with {@link BillCharge} (Strategy) for what a charge
 * <em>is</em>. The sequence of steps is the part that must never vary; which charges exist is the
 * part that may. Splitting them that way means a new charge is a new bean and this class does not
 * change.
 *
 * <p>The subtotal step delegates to {@link Order#subtotal()} rather than re-summing the lines here.
 * That matters beyond DRY: the order's line prices are snapshots, and having exactly one place that
 * turns lines into a subtotal is what stops the bill and the order view ever disagreeing.
 */
@Component
public class BillCalculator {

    private final List<BillCharge> charges;

    public BillCalculator(List<BillCharge> charges) {
        // Injected in @Order sequence, which fixes the order the lines are printed in.
        this.charges = List.copyOf(charges);
    }

    /**
     * The fixed sequence: subtotal &rarr; each charge on that subtotal &rarr; grand total.
     *
     * <p>Each charge is computed against the <em>subtotal</em>, never against a running total —
     * they do not compound (FR-14). Each rounds itself to 2dp, so the lines printed on the bill
     * sum exactly to the total printed beneath them; rounding only at the end is what produces
     * receipts that are a cent out.
     */
    public Bill compute(Order order) {
        BigDecimal subtotal = order.subtotal();

        List<BillLine> lines = charges.stream()
                .map(charge -> new BillLine(charge.label(), charge.amountOn(subtotal)))
                .toList();

        BigDecimal grandTotal = lines.stream()
                .map(BillLine::amount)
                .reduce(subtotal, BigDecimal::add);

        return new Bill(subtotal, lines, grandTotal);
    }
}

package com.restaurant.service.billing;

import java.math.BigDecimal;

/**
 * One charge added on top of an order's subtotal — tax, service charge (FR-14).
 *
 * <p><strong>Design pattern: Strategy.</strong> FR-14 and FR-16 both require tax and service
 * charge as <em>separately named lines</em> rather than one blended figure, so a charge already
 * needs an identity and an amount — that is a Strategy whether or not it is called one. Beans are
 * discovered by the container and sequenced with
 * {@code @org.springframework.core.annotation.Order}, the same arrangement as the FR-08 validator
 * chain: adding a cover charge or a discount is a new bean rather than an edit to
 * {@link BillCalculator} (open/closed).
 *
 * <p><strong>Charges do not compound.</strong> Every implementation is handed the subtotal, never
 * a running total, so the ordering of the beans is presentational only — it decides the order the
 * lines are printed in and nothing else. Do not "fix" this by threading a running total through:
 * FR-14 defines both charges against the subtotal.
 *
 * <p>Implementations must round to 2dp HALF_UP, matching {@code Order.subtotal()}. Rounding per
 * charge rather than only at the end is what makes the printed lines sum exactly to the printed
 * total.
 */
public interface BillCharge {

    /** The name this charge appears under on the bill and the receipt (FR-14, FR-16). */
    String label();

    /** This charge's amount for the given subtotal, rounded to 2dp HALF_UP. Never negative. */
    BigDecimal amountOn(BigDecimal subtotal);
}

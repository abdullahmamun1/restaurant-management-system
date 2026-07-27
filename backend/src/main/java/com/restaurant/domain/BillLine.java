package com.restaurant.domain;

import java.math.BigDecimal;

/**
 * One named charge on a bill — "Tax", "Service charge" — as a label and an amount.
 *
 * <p>FR-14 and FR-16 both require these as <em>separately named</em> lines rather than one blended
 * figure, so a charge needs an identity as well as a value. That is what makes each charge a
 * Strategy rather than a term in a formula.
 */
public record BillLine(String label, BigDecimal amount) {
}

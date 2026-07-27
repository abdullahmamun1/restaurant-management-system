package com.restaurant.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized billing rates, bound from {@code app.billing.*} (mirrors
 * {@code JwtProperties}). Consumed by the {@code BillCharge} strategies.
 *
 * <p>Both are <strong>fractions, not percentages</strong>: {@code 0.05} is 5%. Both apply to the
 * order subtotal and do <em>not</em> compound with each other.
 *
 * <p>Validated in the canonical constructor so a bad value fails the application at startup rather
 * than at the till. A negative rate would produce a negative charge, which would in turn violate
 * the {@code payment_amounts_nonneg} CHECK — surfacing as a failed payment at the worst possible
 * moment instead of a failed boot.
 *
 * @param taxRate            tax as a fraction of the subtotal (FR-14).
 * @param serviceChargeRate  service charge as a fraction of the subtotal (FR-14).
 */
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(BigDecimal taxRate, BigDecimal serviceChargeRate) {

    public BillingProperties {
        taxRate = requireNonNegative(taxRate, "app.billing.tax-rate");
        serviceChargeRate = requireNonNegative(serviceChargeRate, "app.billing.service-charge-rate");
    }

    private static BigDecimal requireNonNegative(BigDecimal rate, String property) {
        if (rate == null) {
            throw new IllegalStateException(property + " must be configured (a fraction, e.g. 0.05).");
        }
        if (rate.signum() < 0) {
            throw new IllegalStateException(
                    property + " must not be negative, but was " + rate.toPlainString() + ".");
        }
        return rate;
    }
}

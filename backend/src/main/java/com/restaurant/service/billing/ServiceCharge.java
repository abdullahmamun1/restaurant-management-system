package com.restaurant.service.billing;

import com.restaurant.config.BillingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Service charge on the order subtotal (FR-14), at the rate configured in
 * {@link BillingProperties}.
 *
 * <p>Applied to the subtotal, <em>not</em> to the subtotal-plus-tax: {@code @Order(20)} places it
 * after {@link TaxCharge} on the printed bill, but charges do not compound (see
 * {@link BillCharge}).
 */
@Component
@Order(20)
public class ServiceCharge implements BillCharge {

    /** See {@link TaxCharge#LABEL} — the receipt names this charge in its own field (FR-16). */
    public static final String LABEL = "Service charge";

    private final BigDecimal rate;

    public ServiceCharge(BillingProperties properties) {
        this.rate = properties.serviceChargeRate();
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    public BigDecimal amountOn(BigDecimal subtotal) {
        return subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}

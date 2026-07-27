package com.restaurant.service.billing;

import com.restaurant.config.BillingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Tax on the order subtotal (FR-14), at the rate configured in {@link BillingProperties}.
 *
 * <p>First charge line by convention ({@code @Order(10)}) — presentational only, since charges do
 * not compound (see {@link BillCharge}).
 */
@Component
@Order(10)
public class TaxCharge implements BillCharge {

    /**
     * The label is a constant because it is not only display text: the {@code payment} row and the
     * receipt both carry tax as their own named field (FR-16), so the mapper has to be able to ask
     * the bill for <em>this</em> charge by name.
     */
    public static final String LABEL = "Tax";

    private final BigDecimal rate;

    public TaxCharge(BillingProperties properties) {
        this.rate = properties.taxRate();
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

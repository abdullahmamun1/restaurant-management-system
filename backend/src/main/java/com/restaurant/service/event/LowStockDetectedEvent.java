package com.restaurant.service.event;

import java.math.BigDecimal;

/**
 * Raised when a stock change leaves an ingredient at or below its low-stock threshold (FR-18).
 *
 * <p>A value record rather than the {@code Ingredient} entity: listeners run
 * {@code AFTER_COMMIT}, by which point the publishing transaction — and with it the entity's
 * persistence context — is gone. Carrying the values means a listener can never accidentally
 * touch a detached entity or lazily load from a closed session.
 *
 * @param ingredientId the ingredient that crossed its threshold.
 * @param name         its name, for the message.
 * @param unit         its unit of measure.
 * @param remaining    stock remaining after the change.
 * @param threshold    the threshold it is at or below.
 */
public record LowStockDetectedEvent(
        Long ingredientId,
        String name,
        String unit,
        BigDecimal remaining,
        BigDecimal threshold) {
}

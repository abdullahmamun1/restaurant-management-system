package com.restaurant.controller.dto;

import com.restaurant.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/**
 * The cashier's confirmation of payment (FR-15). Deliberately just the method: the amount is not
 * an input.
 *
 * <p>A client-supplied total would be a second opinion on money the server has already computed
 * from snapshotted prices, and the only way to handle a disagreement would be to trust one of
 * them. The server computes, the cashier confirms.
 */
public record RecordPaymentRequest(@NotNull PaymentMethod method) {
}

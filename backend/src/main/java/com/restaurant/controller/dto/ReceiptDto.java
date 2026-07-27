package com.restaurant.controller.dto;

import com.restaurant.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The receipt for a paid order (FR-16), carrying exactly what that requirement enumerates: order
 * id, table number, date and time, the items with quantity and unit price, subtotal, tax amount,
 * service charge, and grand total. {@code method} and {@code cashierUsername} are additions — a
 * receipt that does not say how it was paid is not much of a record.
 *
 * <p>Unlike {@link BillDto}, the amounts here are <em>read</em>, not computed: they come from the
 * {@code payment} row, frozen at the moment of payment. That is what makes a reprint months later
 * still correct after the tax rate has changed, and it is why tax and service charge are named
 * fields here rather than the open-ended {@code charges} list of a bill.
 */
public record ReceiptDto(
        Long orderId,
        Long tableId,
        String tableLabel,
        OffsetDateTime paidAt,
        PaymentMethod method,
        String cashierUsername,
        List<OrderItemDto> items,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal serviceCharge,
        BigDecimal grandTotal) {
}

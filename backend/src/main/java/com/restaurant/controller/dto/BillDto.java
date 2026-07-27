package com.restaurant.controller.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The bill for a SERVED order (FR-13, FR-14): every ordered item with its quantity and per-item
 * price, then the subtotal, each named charge, and the grand total.
 *
 * <p>Every figure here is computed server-side. The client never multiplies, sums or rounds money —
 * if it did, a rounding difference between the browser and the receipt would be a bug nobody could
 * reproduce.
 *
 * <p>Available only while the order is SERVED. Once paid, the numbers come from
 * {@link ReceiptDto} instead, read from the frozen {@code payment} row rather than recomputed —
 * see {@code Bill} for why that split exists.
 */
public record BillDto(
        Long orderId,
        Long tableId,
        String tableLabel,
        OffsetDateTime servedAt,
        List<OrderItemDto> items,
        BigDecimal subtotal,
        List<BillLineDto> charges,
        BigDecimal grandTotal) {
}

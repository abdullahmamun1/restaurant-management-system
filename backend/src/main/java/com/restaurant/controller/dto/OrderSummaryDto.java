package com.restaurant.controller.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row of the cashier's worklist: a SERVED order waiting to be settled (FR-13).
 *
 * <p>A summary rather than a full {@link BillDto} per row — the cashier is choosing which table to
 * settle, not reading a bill yet, so the list carries what that choice needs: which table, how
 * long it has been waiting, how many lines, and what it comes to. The itemisation arrives when
 * they open it.
 *
 * <p>{@code grandTotal} is the full bill total including charges, not the subtotal — a worklist
 * showing a figure smaller than the one on the bill would be worse than showing none.
 */
public record OrderSummaryDto(
        Long orderId,
        Long tableId,
        String tableLabel,
        OffsetDateTime servedAt,
        int itemCount,
        BigDecimal grandTotal) {
}

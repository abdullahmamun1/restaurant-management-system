package com.restaurant.controller.dto;

import com.restaurant.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full view of an order, returned by every read <em>and</em> every mutation so the client
 * re-syncs in one round-trip (the live subtotal depends on it).
 *
 * <p>{@code editable} is the server's answer to "may items still be changed?" (FR-07, FR-09) —
 * exposed so the UI disables its controls from the same rule the API enforces rather than
 * re-deriving it from {@code status}. {@code subtotal} is the sum of the line totals; tax and
 * service charge arrive with billing (FR-14, M6).
 */
public record OrderDto(
        Long id,
        Long tableId,
        String tableLabel,
        OrderStatus status,
        String waiterUsername,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime servedAt,
        List<OrderItemDto> items,
        BigDecimal subtotal,
        boolean editable) {
}

package com.restaurant.controller.dto;

import com.restaurant.domain.OrderStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One ticket on the kitchen queue (FR-11) — a deliberately narrower projection of an order than
 * {@link OrderDto}.
 *
 * <p>There is <strong>no money on this DTO</strong>: the kitchen needs the table, the ticket's age
 * and the lines to cook, and nothing about prices, subtotals or which waiter took the order. Not
 * shipping those fields means a kitchen display cannot show figures it has no business showing, and
 * keeps the payload small — it is fetched every ~2.5 s per tablet (NFR-02).
 *
 * <p>{@code waitingSeconds} is computed server-side rather than left to the client: ticket age is
 * what the kitchen triages on, and a tablet with a skewed clock would render a wrong or negative
 * age from {@code confirmedAt}.
 */
public record KitchenTicketDto(
        Long id,
        String tableLabel,
        OrderStatus status,
        OffsetDateTime confirmedAt,
        long waitingSeconds,
        List<KitchenTicketLineDto> items) {
}

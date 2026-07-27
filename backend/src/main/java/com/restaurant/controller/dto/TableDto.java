package com.restaurant.controller.dto;

import com.restaurant.domain.OrderStatus;
import com.restaurant.domain.TableStatus;

/**
 * A table on the floor view.
 *
 * <p>{@code activeOrderId} is the open (unpaid) order on the table, or {@code null} when the table
 * is free — it lets the client jump straight from a table to its order without a second lookup.
 *
 * <p>{@code activeOrderStatus} is that order's position in the lifecycle, and is {@code null}
 * exactly when {@code activeOrderId} is. It is <strong>not</strong> a restatement of
 * {@link #status()}: a table stays OCCUPIED for the whole of an order's life, while the order
 * underneath it moves PENDING &rarr; CONFIRMED &rarr; PREPARING &rarr; READY &rarr; SERVED. The
 * second is the one that tells a waiter what to <em>do</em> — READY means go and collect it — so
 * the floor carries both rather than making them open every table to find out.
 */
public record TableDto(
        Long id,
        String label,
        int seats,
        TableStatus status,
        Long activeOrderId,
        OrderStatus activeOrderStatus) {
}

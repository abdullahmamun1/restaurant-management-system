package com.restaurant.service;

import com.restaurant.controller.dto.KitchenTicketDto;
import com.restaurant.controller.dto.KitchenTicketLineDto;
import com.restaurant.domain.Order;
import com.restaurant.domain.OrderItem;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps orders to the kitchen queue projection (DTO + Mapper pattern), keeping entities out of the
 * REST boundary (NFR-05).
 *
 * <p>Separate from {@link OrderMapper} because it answers a different question: {@code OrderMapper}
 * produces the waiter's and cashier's full view of an order, this one produces the cook's view of a
 * ticket. Folding both into one class would mean a mapper whose output depends on who is asking.
 *
 * <p>Must be invoked inside the service transaction — {@link Order#getItems()} is lazy (though
 * {@code findQueueWithItems} has already fetched it on the queue path).
 */
@Component
public class KitchenMapper {

    /** Projects an order onto a queue ticket, stamping its age at the moment of the read. */
    public KitchenTicketDto toTicket(Order order) {
        return new KitchenTicketDto(
                order.getId(),
                order.getTable().getLabel(),
                order.getStatus(),
                order.getConfirmedAt(),
                waitingSeconds(order.getConfirmedAt()),
                order.getItems().stream().map(this::toLine).toList());
    }

    private KitchenTicketLineDto toLine(OrderItem line) {
        return new KitchenTicketLineDto(
                line.getMenuItem().getName(),
                line.getQuantity(),
                line.getNotes());
    }

    /**
     * How long the ticket has been waiting. Clamped at zero so a clock adjustment between the
     * confirm and the read can never produce a negative age, and defensive against a null
     * {@code confirmedAt} — unreachable for a queued order, but this mapper should not be the thing
     * that throws if that ever changes.
     */
    private long waitingSeconds(OffsetDateTime confirmedAt) {
        if (confirmedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(confirmedAt, OffsetDateTime.now()).toSeconds());
    }
}

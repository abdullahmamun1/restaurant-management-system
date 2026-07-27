package com.restaurant.service;

import com.restaurant.controller.dto.KitchenTicketDto;
import com.restaurant.domain.OrderStatus;
import com.restaurant.repository.OrderRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The kitchen's read side (FR-11): the live queue of work.
 *
 * <p>Read-only by design. The kitchen's two <em>writes</em> (PREPARING, READY) are lifecycle
 * transitions on the order and live with every other transition in {@link OrderService}, so no
 * status rule is duplicated here — this service exists only because the queue is its own
 * projection (see {@link KitchenMapper}), with its own access rule under SRS §2.1.
 */
@Service
public class KitchenService {

    /**
     * What "in the kitchen queue" means, defined once: FR-11's CONFIRMED and PREPARING, and by
     * omission FR-12's "orders persist in the queue until marked READY". Inlining this set at the
     * call site would leave the queue's membership rule stated nowhere.
     */
    private static final Set<OrderStatus> QUEUE_STATUSES =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING);

    private final OrderRepository orderRepository;
    private final KitchenMapper mapper;

    public KitchenService(OrderRepository orderRepository, KitchenMapper mapper) {
        this.orderRepository = orderRepository;
        this.mapper = mapper;
    }

    /** The live queue, oldest confirmation first (FR-11). */
    @Transactional(readOnly = true)
    public List<KitchenTicketDto> getQueue() {
        return orderRepository.findQueueWithItems(QUEUE_STATUSES).stream()
                .map(mapper::toTicket)
                .toList();
    }
}

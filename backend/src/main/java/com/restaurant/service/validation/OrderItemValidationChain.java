package com.restaurant.service.validation;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Runs the FR-08 order-item validators in sequence, stopping at the first rejection.
 *
 * <p><strong>Design pattern: Chain of Responsibility</strong> — in the container-assembled variant
 * rather than the classic one where each handler holds a {@code next} pointer. Spring injects
 * every {@link OrderItemValidator} bean in {@code @Order} sequence, and the first link to throw
 * ends the walk. The property that matters is preserved: {@code OrderService} knows only "validate
 * this", not which rules exist or in what order, and a new rule is a new bean rather than an edit
 * to the service (open/closed).
 *
 * <p>Because every link is read-only and this runs <em>before</em> the order is touched, a
 * rejection leaves the order unchanged as FR-08 requires — and the surrounding
 * {@code @Transactional} boundary makes that hold even if a later step fails.
 */
@Component
public class OrderItemValidationChain {

    private final List<OrderItemValidator> links;

    public OrderItemValidationChain(List<OrderItemValidator> links) {
        this.links = List.copyOf(links);
    }

    /**
     * Applies every rule to the request.
     *
     * @throws RuntimeException from the first link that rejects, carrying a message describing why.
     */
    public void validate(OrderItemValidationContext context) {
        for (OrderItemValidator link : links) {
            link.validate(context);
        }
    }
}

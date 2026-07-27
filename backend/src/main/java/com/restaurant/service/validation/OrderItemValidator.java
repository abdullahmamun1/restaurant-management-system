package com.restaurant.service.validation;

/**
 * One link in the order-item validation chain (FR-08).
 *
 * <p><strong>Design pattern: Chain of Responsibility.</strong> A link either passes the request on
 * (by returning) or rejects it (by throwing a descriptive exception that the global handler maps
 * to 409). Links are contributed by the Spring container and sequenced with
 * {@code @org.springframework.core.annotation.Order} — see {@link OrderItemValidationChain}.
 *
 * <p>Adding a new rule means adding a bean; neither {@code OrderService} nor the existing links
 * change (open/closed).
 */
public interface OrderItemValidator {

    /**
     * Checks one rule against the request.
     *
     * @throws RuntimeException a descriptive domain exception if the item must be rejected.
     */
    void validate(OrderItemValidationContext context);
}

package com.restaurant.service.validation;

import com.restaurant.domain.MenuItem;
import com.restaurant.service.exception.ConflictException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * First link of the FR-08 chain: the menu item must be marked available (FR-05, FR-08a).
 *
 * <p>Runs before the stock check because it is the cheaper test and the more likely rejection —
 * an "86'd" item should not cost a recipe lookup.
 */
@Component
@Order(10)
public class MenuItemAvailabilityValidator implements OrderItemValidator {

    @Override
    public void validate(OrderItemValidationContext context) {
        MenuItem item = context.menuItem();
        if (!item.isAvailable()) {
            throw new ConflictException("'" + item.getName()
                    + "' is currently unavailable and cannot be added to the order.");
        }
    }
}

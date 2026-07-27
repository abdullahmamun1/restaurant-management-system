package com.restaurant.controller;

import com.restaurant.controller.dto.ServiceFlagRequest;
import com.restaurant.controller.dto.TableDto;
import com.restaurant.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dining-floor endpoints. Thin controller — delegates to {@link OrderService}.
 *
 * <p>RBAC per SRS §2.1 (Table &amp; Orders): the Waiter has full access; Manager, Kitchen and
 * Cashier may read. Enforced server-side with {@code @PreAuthorize} (NFR-03).
 */
@RestController
@RequestMapping("/tables")
public class TableController {

    private static final String CAN_READ = "hasAnyRole('MANAGER','WAITER','KITCHEN','CASHIER')";
    private static final String CAN_WRITE = "hasRole('WAITER')";

    private final OrderService orderService;

    public TableController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public List<TableDto> list() {
        return orderService.listTables();
    }

    @PatchMapping("/{id}/service-flag")
    @PreAuthorize(CAN_WRITE)
    public TableDto setServiceFlag(@PathVariable Long id,
                                   @Valid @RequestBody ServiceFlagRequest request) {
        return orderService.setServiceFlag(id, request.needsService());
    }
}

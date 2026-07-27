package com.restaurant.controller;

import com.restaurant.controller.dto.AddOrderItemRequest;
import com.restaurant.controller.dto.ChangeItemQuantityRequest;
import com.restaurant.controller.dto.CreateOrderRequest;
import com.restaurant.controller.dto.OrderDto;
import com.restaurant.domain.OrderStatus;
import com.restaurant.service.OrderService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order endpoints (FR-06–FR-10). Thin controller — delegates to {@link OrderService}.
 *
 * <p>RBAC per SRS §2.1: for Table &amp; Orders the Waiter has full access and Manager, Kitchen and
 * Cashier may read; the two Kitchen Queue transitions below are Kitchen-only (NFR-03).
 *
 * <p>Transitions are exposed as intention-revealing action endpoints ({@code /confirm},
 * {@code /prepare}, {@code /ready}, {@code /serve}) rather than a single {@code PATCH /status},
 * because <em>the role permitted to make a transition depends on the target state</em>. That is not
 * a stylistic preference: {@code READY → SERVED} is a legal transition, so one generic endpoint
 * guarded for the Kitchen would let a cook mark an order SERVED — the Waiter's action under FR-10.
 * Separate endpoints keep each rule in its own {@code @PreAuthorize} instead of pushing a
 * target-status whitelist down into the service. The Cashier's payment (M6, FR-15) will arrive the
 * same way. All of them are non-idempotent by design: repeating one gets a 409 from the State
 * machine, which is the right answer to a double-tap.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final String CAN_READ = "hasAnyRole('MANAGER','WAITER','KITCHEN','CASHIER')";
    private static final String CAN_WRITE = "hasRole('WAITER')";
    private static final String CAN_COOK = "hasRole('KITCHEN')";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public List<OrderDto> list(@RequestParam(required = false) OrderStatus status,
                               @RequestParam(required = false) Long tableId) {
        return orderService.listOrders(status, tableId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public OrderDto get(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto create(@Valid @RequestBody CreateOrderRequest request, Principal principal) {
        return orderService.createOrder(request.tableId(), principal.getName());
    }

    @PostMapping("/{id}/items")
    @PreAuthorize(CAN_WRITE)
    public OrderDto addItem(@PathVariable Long id,
                            @Valid @RequestBody AddOrderItemRequest request) {
        return orderService.addItem(id, request.menuItemId(), request.quantity(), request.notes());
    }

    /**
     * Sets a line's quantity (FR-07). {@code PATCH} rather than a pair of increment/decrement
     * actions: the request carries the quantity the waiter wants, so repeated taps are idempotent
     * in effect and cannot interleave into the wrong total.
     */
    @PatchMapping("/{id}/items/{itemId}")
    @PreAuthorize(CAN_WRITE)
    public OrderDto changeItemQuantity(@PathVariable Long id, @PathVariable Long itemId,
                                       @Valid @RequestBody ChangeItemQuantityRequest request) {
        return orderService.changeItemQuantity(id, itemId, request.quantity());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize(CAN_WRITE)
    public OrderDto removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return orderService.removeItem(id, itemId);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(CAN_WRITE)
    public OrderDto confirm(@PathVariable Long id) {
        return orderService.confirm(id);
    }

    /** Kitchen starts cooking (FR-12). The ticket stays on the queue. */
    @PostMapping("/{id}/prepare")
    @PreAuthorize(CAN_COOK)
    public OrderDto prepare(@PathVariable Long id) {
        return orderService.markPreparing(id);
    }

    /** Kitchen is finished (FR-12). The ticket leaves the queue. */
    @PostMapping("/{id}/ready")
    @PreAuthorize(CAN_COOK)
    public OrderDto ready(@PathVariable Long id) {
        return orderService.markReady(id);
    }

    @PostMapping("/{id}/serve")
    @PreAuthorize(CAN_WRITE)
    public OrderDto serve(@PathVariable Long id) {
        return orderService.markServed(id);
    }
}

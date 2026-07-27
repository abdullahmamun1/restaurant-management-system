package com.restaurant.controller;

import com.restaurant.controller.dto.BillDto;
import com.restaurant.controller.dto.OrderSummaryDto;
import com.restaurant.controller.dto.ReceiptDto;
import com.restaurant.controller.dto.RecordPaymentRequest;
import com.restaurant.service.BillingService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing &amp; Payment endpoints (FR-13–FR-16). Thin controller — delegates to
 * {@link BillingService}.
 *
 * <p>RBAC per SRS §2.1, where Billing &amp; Payment is Cashier <em>Full</em> and Manager
 * <em>Read</em>: the read guard sits at class level and the one write narrows it, mirroring
 * {@link OrderController}. A manager can therefore look at any bill or receipt but cannot take
 * money, and that rule lives entirely in the annotations — nothing in the service asks who is
 * calling (NFR-03).
 *
 * <p>Payment is a {@code POST} action endpoint rather than a status patch, for the same reason the
 * kitchen's transitions are: which role may make a transition depends on the target state, and
 * {@code POST /orders/{id}/payment} says what it does. It is non-idempotent by design — a second
 * call gets a 409 from the State machine, which is the right answer to a double-tap.
 */
@RestController
@RequestMapping("/billing")
@PreAuthorize(BillingController.CAN_READ)
public class BillingController {

    static final String CAN_READ = "hasAnyRole('MANAGER','CASHIER')";
    private static final String CAN_SETTLE = "hasRole('CASHIER')";

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** SERVED orders waiting to be settled, longest-served first (FR-13). */
    @GetMapping("/orders")
    public List<OrderSummaryDto> payableOrders() {
        return billingService.listPayable();
    }

    /** The bill for a served order: itemisation (FR-13) and the total breakdown (FR-14). */
    @GetMapping("/orders/{id}/bill")
    public BillDto bill(@PathVariable Long id) {
        return billingService.getBill(id);
    }

    /**
     * Records payment and settles the order (FR-15): status PAID, table AVAILABLE, and the
     * automatic atomic ingredient deduction (FR-19). Returns the receipt (FR-16), so the whole
     * settlement is a single round-trip.
     */
    @PostMapping("/orders/{id}/payment")
    @PreAuthorize(CAN_SETTLE)
    public ReceiptDto pay(@PathVariable Long id,
                          @Valid @RequestBody RecordPaymentRequest request,
                          Principal principal) {
        return billingService.pay(id, request.method(), principal.getName());
    }

    /** Reprints the receipt for a settled order (FR-16). */
    @GetMapping("/orders/{id}/receipt")
    public ReceiptDto receipt(@PathVariable Long id) {
        return billingService.getReceipt(id);
    }
}

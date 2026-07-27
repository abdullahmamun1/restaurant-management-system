package com.restaurant.service;

import com.restaurant.config.BillingProperties;
import com.restaurant.controller.dto.BillDto;
import com.restaurant.controller.dto.OrderSummaryDto;
import com.restaurant.controller.dto.ReceiptDto;
import com.restaurant.domain.Bill;
import com.restaurant.domain.IllegalOrderStateException;
import com.restaurant.domain.Ingredient;
import com.restaurant.domain.Order;
import com.restaurant.domain.OrderStatus;
import com.restaurant.domain.Payment;
import com.restaurant.domain.PaymentMethod;
import com.restaurant.domain.User;
import com.restaurant.repository.OrderRepository;
import com.restaurant.repository.PaymentRepository;
import com.restaurant.repository.UserRepository;
import com.restaurant.service.billing.BillCalculator;
import com.restaurant.service.billing.ServiceCharge;
import com.restaurant.service.billing.TaxCharge;
import com.restaurant.service.event.LowStockDetectedEvent;
import com.restaurant.service.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing and payment business logic (FR-13–FR-16, FR-19).
 *
 * <p><strong>Bill and receipt are two different things, deliberately.</strong> A bill exists only
 * while an order is SERVED and is <em>computed</em> from it; a receipt exists only once the order
 * is PAID and is <em>read</em> from the frozen {@code payment} row. Two status-guarded endpoints
 * with disjoint meanings beat one endpoint whose response shape depends on status — and a reprint
 * comes free, which a cashier who closed the tab mid-shift needs.
 *
 * <p>This service orchestrates; it computes nothing itself. {@link BillCalculator} owns the
 * arithmetic, {@link InventoryDeductionService} owns the stock movements, the {@code State} machine
 * on {@link Order} owns what transitions are legal, and {@link Ingredient} owns the non-negative
 * invariant. What lives here is the {@code @Transactional} boundary and the order of the steps —
 * which, for {@link #pay}, is the requirement.
 */
@Service
public class BillingService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final BillCalculator billCalculator;
    private final InventoryDeductionService inventoryDeductionService;
    private final BillingMapper mapper;
    private final ApplicationEventPublisher events;
    /**
     * Read for the rate <em>snapshot</em> written onto the payment row, not to compute anything —
     * the amounts come from {@link BillCalculator}. Taking the rates from the same configuration
     * the charge beans read keeps this service from having to know which concrete
     * {@code BillCharge} implementations exist.
     */
    private final BillingProperties billingProperties;

    public BillingService(OrderRepository orderRepository,
                          PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          BillCalculator billCalculator,
                          InventoryDeductionService inventoryDeductionService,
                          BillingMapper mapper,
                          ApplicationEventPublisher events,
                          BillingProperties billingProperties) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.billCalculator = billCalculator;
        this.inventoryDeductionService = inventoryDeductionService;
        this.mapper = mapper;
        this.events = events;
        this.billingProperties = billingProperties;
    }

    // ---- Reads (FR-13, FR-14, FR-16) ---------------------------------------

    /**
     * The cashier's worklist: SERVED orders awaiting settlement, longest-served first (FR-13).
     *
     * <p>Costs one query per order for its lines, which is what the plain list finders trade for
     * not fetch-joining. This list is a handful of tables long and is read on demand rather than
     * polled, so that is the cheaper side of the trade here.
     */
    @Transactional(readOnly = true)
    public List<OrderSummaryDto> listPayable() {
        return orderRepository.findByStatusOrderByServedAtAsc(OrderStatus.SERVED).stream()
                .map(order -> mapper.toSummary(order, billCalculator.compute(order)))
                .toList();
    }

    /** The full bill for a served order: itemisation (FR-13) and totals (FR-14). */
    @Transactional(readOnly = true)
    public BillDto getBill(Long orderId) {
        Order order = requireOrder(orderId);
        requireStatus(order, OrderStatus.SERVED,
                "A bill can only be drawn up for an order that has been served");
        return mapper.toDto(order, billCalculator.compute(order));
    }

    /**
     * The receipt for a settled order (FR-16), re-retrievable any number of times. Its amounts are
     * the snapshot taken at payment, so a reprint never disagrees with the original.
     */
    @Transactional(readOnly = true)
    public ReceiptDto getReceipt(Long orderId) {
        Order order = requireOrder(orderId);
        requireStatus(order, OrderStatus.PAID,
                "A receipt only exists once an order has been paid");
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order " + orderId + " is PAID but has no payment record."));
        return mapper.toReceipt(payment);
    }

    // ---- Payment (FR-15, FR-19, NFR-04, NFR-07) -----------------------------

    /**
     * Settles a served order in <strong>one transaction</strong> (FR-15, NFR-04): compute the bill,
     * record the payment, mark the order PAID, free the table, and deduct every recipe ingredient.
     * Any failure rolls back all of it — there is no partial state in which money is recorded but
     * stock is not, or the reverse.
     *
     * <p>The step order is load-bearing, not incidental:
     * <ol>
     *   <li><strong>Compute the bill first</strong>, before anything mutates, so the amounts
     *       recorded are the ones the cashier was shown.</li>
     *   <li><strong>{@code markPaid()} early.</strong> The State machine refuses anything but
     *       SERVED &rarr; PAID, so a double-tap or a not-yet-served order fails here as a clean
     *       409 rather than part-way through a payment.</li>
     *   <li><strong>Deduct last.</strong> It is the step most likely to fail and the only one that
     *       takes row locks; doing it last keeps everything before it cheap to roll back and holds
     *       the ingredient locks for the shortest possible window.</li>
     * </ol>
     *
     * <p>If stock has fallen since the order was validated — the unavoidable consequence of FR-08
     * not reserving (M4 D1) — the deduction throws, the whole transaction rolls back, and the
     * cashier gets a 409 naming the ingredient and the shortfall. The order stays SERVED and the
     * table stays occupied, so the bill can be settled again once a manager restocks (FR-21). That
     * is exactly what FR-22 prescribes: halt, and notify the responsible party.
     */
    @Transactional
    public ReceiptDto pay(Long orderId, PaymentMethod method, String cashierUsername) {
        Order order = requireOrder(orderId);
        User cashier = userRepository.findByUsername(cashierUsername)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User '" + cashierUsername + "' not found."));

        Bill bill = billCalculator.compute(order);

        order.markPaid();                 // 409 unless SERVED; PAID is terminal, so never twice
        order.getTable().release();       // FR-15(b)

        Payment payment = paymentRepository.save(new Payment(
                order,
                cashier,
                method,
                bill.subtotal(),
                bill.amountOf(TaxCharge.LABEL),
                bill.amountOf(ServiceCharge.LABEL),
                bill.grandTotal(),
                billingProperties.taxRate(),
                billingProperties.serviceChargeRate()));

        // FR-15(c) / FR-19 — same transaction, never REQUIRES_NEW (see InventoryDeductionService).
        List<Ingredient> touched = inventoryDeductionService.deductForOrder(order);
        publishLowStockAlerts(touched);

        return mapper.toReceipt(payment);
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * Announces any ingredient the payment left at or below its threshold (FR-18).
     *
     * <p>Published inside the transaction but delivered {@code AFTER_COMMIT} (see
     * {@code LowStockAlertListener}), which is what keeps alerting from being able to affect the
     * payment in either direction: a rolled-back payment raises nothing, and a failing listener
     * cannot fail a payment that already succeeded.
     */
    private void publishLowStockAlerts(List<Ingredient> touched) {
        for (Ingredient ingredient : touched) {
            if (ingredient.isLowStock()) {
                events.publishEvent(new LowStockDetectedEvent(
                        ingredient.getId(),
                        ingredient.getName(),
                        ingredient.getUnit(),
                        ingredient.getStockQty(),
                        ingredient.getLowStockThreshold()));
            }
        }
    }

    private Order requireOrder(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found."));
    }

    /** A state-based refusal (409), phrased so the cashier knows what the order is actually doing. */
    private void requireStatus(Order order, OrderStatus required, String what) {
        if (order.getStatus() != required) {
            throw new IllegalOrderStateException(what + ", but order " + order.getId() + " is "
                    + order.getStatus() + ".");
        }
    }
}

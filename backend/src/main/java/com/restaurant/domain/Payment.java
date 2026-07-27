package com.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The record that an order was settled (FR-15) and, for the rest of time, what it was settled
 * <em>for</em> (FR-16).
 *
 * <p><strong>Immutable after construction</strong>, in the same spirit as
 * {@link InventoryAdjustment}: no setters, no update path, and no service method that mutates one.
 * "No refunds, cancellations or modifications after payment" is a stated non-goal, so the absence
 * of a mutator is the enforcement, not an oversight.
 *
 * <p>The four amounts are a <strong>snapshot</strong>, not a cache. An unpaid bill is computed from
 * the order — {@code order_item.unit_price} is itself snapshotted, so the line totals cannot move —
 * but the tax and service-charge <em>rates</em> live in configuration and may change. Freezing the
 * amounts (and the rates that produced them) here is what makes a reprinted receipt still true a
 * year later. Maps to {@code payment}.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One payment per order (SRS §2.3) — backed by the {@code UNIQUE} constraint on the column. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    /** The cashier who took the money — recorded for audit and for M7's reporting. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "service_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceCharge;

    @Column(name = "grand_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "service_charge_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal serviceChargeRate;

    /**
     * Set by the application rather than left to the column default (as {@code Order.createdAt} is,
     * and for the same reason): {@code ReceiptDto} returns it from the payment call, and a DB-side
     * default would come back null until the entity was re-read. The DB default remains as a
     * backstop.
     */
    @Column(name = "paid_at", nullable = false, updatable = false)
    private OffsetDateTime paidAt;

    protected Payment() {
        // Required by JPA.
    }

    public Payment(Order order, User cashier, PaymentMethod method,
                   BigDecimal subtotal, BigDecimal taxAmount, BigDecimal serviceCharge,
                   BigDecimal grandTotal, BigDecimal taxRate, BigDecimal serviceChargeRate) {
        this.order = order;
        this.cashier = cashier;
        this.method = method;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.serviceCharge = serviceCharge;
        this.grandTotal = grandTotal;
        this.taxRate = taxRate;
        this.serviceChargeRate = serviceChargeRate;
        this.paidAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public User getCashier() {
        return cashier;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getServiceCharge() {
        return serviceCharge;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public BigDecimal getServiceChargeRate() {
        return serviceChargeRate;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }
}

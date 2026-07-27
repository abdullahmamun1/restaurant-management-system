package com.restaurant.service;

import com.restaurant.controller.dto.BillDto;
import com.restaurant.controller.dto.BillLineDto;
import com.restaurant.controller.dto.OrderSummaryDto;
import com.restaurant.controller.dto.ReceiptDto;
import com.restaurant.domain.Bill;
import com.restaurant.domain.Order;
import com.restaurant.domain.Payment;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps orders, bills and payments to the billing DTOs (DTO + Mapper pattern), so entities never
 * cross the REST boundary (NFR-05).
 *
 * <p>Reuses {@link OrderMapper} for the line itemisation: an order line means the same thing to a
 * cashier as it does to a waiter, and {@code OrderItemDto} already carries the quantity, the
 * snapshotted unit price and the line total that FR-13 and FR-16 ask for. Where the audience
 * genuinely differs, the DTO differs — see {@code KitchenTicketDto}, which carries no money at all.
 *
 * <p>Must be invoked inside the service transaction: {@link Order#getItems()} is lazy.
 */
@Component
public class BillingMapper {

    private final OrderMapper orderMapper;

    public BillingMapper(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /** The computed bill for a served order (FR-13, FR-14). */
    public BillDto toDto(Order order, Bill bill) {
        List<BillLineDto> charges = bill.charges().stream()
                .map(line -> new BillLineDto(line.label(), line.amount()))
                .toList();

        return new BillDto(
                order.getId(),
                order.getTable().getId(),
                order.getTable().getLabel(),
                order.getServedAt(),
                order.getItems().stream().map(orderMapper::toDto).toList(),
                bill.subtotal(),
                charges,
                bill.grandTotal());
    }

    /**
     * The receipt for a settled order (FR-16). Every amount is read from the {@link Payment}
     * snapshot rather than recomputed, so a reprint is identical to the original however long
     * afterwards it happens and whatever the configured rates have since become.
     */
    public ReceiptDto toReceipt(Payment payment) {
        Order order = payment.getOrder();
        return new ReceiptDto(
                order.getId(),
                order.getTable().getId(),
                order.getTable().getLabel(),
                payment.getPaidAt(),
                payment.getMethod(),
                payment.getCashier().getUsername(),
                order.getItems().stream().map(orderMapper::toDto).toList(),
                payment.getSubtotal(),
                payment.getTaxAmount(),
                payment.getServiceCharge(),
                payment.getGrandTotal());
    }

    /** One row of the cashier's worklist. */
    public OrderSummaryDto toSummary(Order order, Bill bill) {
        return new OrderSummaryDto(
                order.getId(),
                order.getTable().getId(),
                order.getTable().getLabel(),
                order.getServedAt(),
                order.getItems().size(),
                bill.grandTotal());
    }
}

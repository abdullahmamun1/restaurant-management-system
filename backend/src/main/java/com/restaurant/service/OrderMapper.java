package com.restaurant.service;

import com.restaurant.controller.dto.OrderDto;
import com.restaurant.controller.dto.OrderItemDto;
import com.restaurant.controller.dto.TableDto;
import com.restaurant.domain.Order;
import com.restaurant.domain.OrderItem;
import com.restaurant.domain.RestaurantTable;
import org.springframework.stereotype.Component;

/**
 * Maps table/order entities to boundary DTOs (DTO + Mapper pattern) so entities never leak past
 * the service layer (NFR-05).
 *
 * <p>Must be invoked inside the service transaction: {@link Order#getItems()} is lazy.
 */
@Component
public class OrderMapper {

    /**
     * A table for the floor view, together with the open order on it if there is one.
     *
     * <p>Takes the {@link Order} rather than its id so the id and the status cannot disagree: two
     * nullable parameters could be passed inconsistently, one order cannot.
     */
    public TableDto toDto(RestaurantTable table, Order activeOrder) {
        return new TableDto(
                table.getId(),
                table.getLabel(),
                table.getSeats(),
                table.getStatus(),
                activeOrder == null ? null : activeOrder.getId(),
                activeOrder == null ? null : activeOrder.getStatus());
    }

    public OrderItemDto toDto(OrderItem line) {
        return new OrderItemDto(
                line.getId(),
                line.getMenuItem().getId(),
                line.getMenuItem().getName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.lineTotal(),
                line.getNotes());
    }

    public OrderDto toDto(Order order) {
        return new OrderDto(
                order.getId(),
                order.getTable().getId(),
                order.getTable().getLabel(),
                order.getStatus(),
                order.getWaiter().getUsername(),
                order.getCreatedAt(),
                order.getConfirmedAt(),
                order.getServedAt(),
                order.getItems().stream().map(this::toDto).toList(),
                order.subtotal(),
                order.getStatus().allowsItemEdits());
    }
}

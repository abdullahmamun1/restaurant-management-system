package com.restaurant.controller.dto;

/**
 * A single line to cook on a {@link KitchenTicketDto}: what, how many, and the guest's note.
 *
 * <p>No unit price or line total — see {@link KitchenTicketDto}. {@code notes} is null when the
 * guest asked for nothing special.
 */
public record KitchenTicketLineDto(String menuItemName, int quantity, String notes) {
}

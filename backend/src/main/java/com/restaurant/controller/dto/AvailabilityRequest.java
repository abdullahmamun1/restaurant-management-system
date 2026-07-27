package com.restaurant.controller.dto;

/** Toggle payload for a menu item's availability flag (FR-04). */
public record AvailabilityRequest(boolean available) {
}

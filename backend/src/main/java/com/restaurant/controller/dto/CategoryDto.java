package com.restaurant.controller.dto;

/** Public view of a menu category. */
public record CategoryDto(Long id, String name, int sortOrder) {
}

package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Create/update payload for a menu category (FR-03). */
public record CategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @PositiveOrZero int sortOrder) {
}

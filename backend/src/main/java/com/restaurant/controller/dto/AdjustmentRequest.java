package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A manual stock adjustment (FR-21). {@code quantityChange} is signed: positive to add stock,
 * negative to remove. A reason is required for the audit log.
 */
public record AdjustmentRequest(
        @NotNull BigDecimal quantityChange,
        @NotBlank @Size(max = 300) String reason) {
}

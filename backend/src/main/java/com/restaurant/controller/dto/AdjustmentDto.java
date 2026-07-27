package com.restaurant.controller.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A read-only audit-log entry for a stock adjustment (FR-21, NFR-06). */
public record AdjustmentDto(
        Long id,
        BigDecimal quantityChange,
        String reason,
        BigDecimal resultingStock,
        String managerUsername,
        OffsetDateTime createdAt) {
}

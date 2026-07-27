package com.restaurant.controller.dto;

import java.math.BigDecimal;

/**
 * One named charge on a bill — "Tax", "Service charge" (FR-14).
 *
 * <p>Sent as a labelled list rather than fixed fields so the client renders whatever charges the
 * server applied, in the server's order, without knowing what they are. The receipt is the one
 * place that does name them individually, because FR-16 enumerates them.
 */
public record BillLineDto(String label, BigDecimal amount) {
}

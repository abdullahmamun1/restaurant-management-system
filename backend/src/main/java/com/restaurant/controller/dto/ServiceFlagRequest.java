package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Raises or clears the NEEDS_SERVICE flag on an occupied table. Informational only — it does not
 * affect the order lifecycle.
 */
public record ServiceFlagRequest(@NotNull Boolean needsService) {
}

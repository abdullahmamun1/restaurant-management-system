package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sets a new password for an account.
 *
 * <p>Deliberately a <em>manager reset</em>, not a self-service change, so it asks for no current
 * password — a manager resetting a forgotten one does not know it. The trade is stated rather than
 * hidden: this endpoint is Manager-only and a manager can therefore set any staff password, which
 * is the intended authority in a shop-floor system where the alternative is an account nobody can
 * get into.
 */
public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String password) {
}

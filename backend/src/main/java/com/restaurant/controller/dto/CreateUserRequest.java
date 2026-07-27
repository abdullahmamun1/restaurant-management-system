package com.restaurant.controller.dto;

import com.restaurant.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pre-registers a staff account. There is no self-signup — a manager creates every account, so this
 * is the only way one comes into existence.
 *
 * <p>The password arrives in plaintext over HTTPS and is encoded before it reaches the entity; it is
 * never stored, logged or returned. {@link UserDto} has no password field at all.
 */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                message = "may contain only letters, digits, dot, underscore or hyphen")
        String username,

        @NotBlank @Size(min = 8, max = 100) String password,

        @NotNull Role role,

        @NotBlank @Size(max = 120) String fullName) {
}

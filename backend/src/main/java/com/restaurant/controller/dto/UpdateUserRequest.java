package com.restaurant.controller.dto;

import com.restaurant.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edits the parts of an account a manager owns: the display name and the role.
 *
 * <p>No username and no password. The username identifies the person in the audit trail and must
 * not drift; the password has its own endpoint because resetting one is a different act, with
 * different consequences, from correcting a spelling.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotNull Role role) {
}

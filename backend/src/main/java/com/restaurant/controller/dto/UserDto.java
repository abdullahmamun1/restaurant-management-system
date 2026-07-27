package com.restaurant.controller.dto;

import com.restaurant.domain.Role;

/** Public view of a user — never exposes the password hash. */
public record UserDto(Long id, String username, Role role, String fullName, boolean enabled) {
}

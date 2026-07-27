package com.restaurant.controller.dto;

import com.restaurant.domain.Role;
import java.time.Instant;

/** Result of a successful login: the bearer token plus the authenticated user's identity. */
public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        String username,
        Role role,
        String fullName) {

    public static LoginResponse bearer(String token, Instant expiresAt,
                                       String username, Role role, String fullName) {
        return new LoginResponse(token, "Bearer", expiresAt, username, role, fullName);
    }
}

package com.restaurant.domain;

/**
 * The four fixed staff roles (SRS §2.1). Role names are persisted as strings and drive
 * RBAC: each maps to a Spring Security authority {@code ROLE_<name>}.
 */
public enum Role {
    MANAGER,
    WAITER,
    KITCHEN,
    CASHIER
}

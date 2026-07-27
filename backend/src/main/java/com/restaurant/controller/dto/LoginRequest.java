package com.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** Login credentials sent by the client (FR-01). */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}

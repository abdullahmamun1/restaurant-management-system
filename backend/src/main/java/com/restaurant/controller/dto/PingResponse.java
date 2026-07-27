package com.restaurant.controller.dto;

/**
 * Response DTO for the M0 liveness endpoint. Establishes the convention that every REST
 * response is a typed DTO at the boundary (never a raw Map or entity). Serializes to
 * {@code {"status":"ok"}}.
 */
public record PingResponse(String status) {
}

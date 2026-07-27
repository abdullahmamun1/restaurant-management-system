package com.restaurant.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized JWT settings, bound from {@code app.security.jwt.*}.
 *
 * @param secret            HMAC-SHA signing secret; must be at least 32 bytes for HS256. Supply a
 *                          real value via env/local config in any shared environment —
 *                          {@link ProductionSecretCheck} refuses to start with the shipped default
 *                          when the {@code prod} profile is active.
 * @param expirationMinutes token lifetime in minutes.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, long expirationMinutes) {

    /**
     * The development value in {@code application.yml}. Named here so it can be <em>recognised and
     * rejected</em>, not so it can be used: a default that works everywhere is a default that
     * eventually ships, and a signing key in a public repository is the worst failure NFR-03 has.
     */
    static final String DEV_DEFAULT_SECRET =
            "dev-only-change-me-restaurant-system-jwt-secret-key-32b+";
}

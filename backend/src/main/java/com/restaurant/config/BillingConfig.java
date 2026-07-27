package com.restaurant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the billing configuration properties, the same way {@code SecurityConfig} registers
 * {@code JwtProperties} — each area owns the binding of its own settings rather than piling them
 * onto the application entry point.
 */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingConfig {
}

package com.restaurant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the reporting configuration properties, the same way {@link BillingConfig} registers
 * {@link BillingProperties} — each area owns the binding of its own settings.
 */
@Configuration
@EnableConfigurationProperties(ReportingProperties.class)
public class ReportingConfig {
}

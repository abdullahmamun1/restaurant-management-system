package com.restaurant.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized reporting settings, bound from {@code app.reporting.*} (mirrors
 * {@link BillingProperties} and {@code JwtProperties}).
 *
 * <p>The zone is <strong>the restaurant's</strong> local timezone, not the server's. Report date
 * ranges are calendar days there (M7 D2): at {@code +06}, a payment taken at 19:30 UTC belongs to
 * the next local day's sales. Deliberately configuration rather than
 * {@code ZoneId.systemDefault()}, so a report does not change meaning when the application is
 * deployed somewhere else.
 *
 * <p>Spring converts {@code String -> ZoneId} out of the box, so an unknown zone id fails the
 * application at <em>startup</em> — which is the right time to find out. The null check below covers
 * the property being absent entirely.
 *
 * @param zone the restaurant's local timezone, e.g. {@code Asia/Dhaka}.
 */
@ConfigurationProperties(prefix = "app.reporting")
public record ReportingProperties(ZoneId zone) {

    public ReportingProperties {
        if (zone == null) {
            throw new IllegalStateException(
                    "app.reporting.zone must be configured (an IANA zone id, e.g. Asia/Dhaka).");
        }
    }
}

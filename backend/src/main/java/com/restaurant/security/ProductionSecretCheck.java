package com.restaurant.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start under the {@code prod} profile while the shipped development signing secret is
 * still in place (M8 D9).
 *
 * <p>Fails at boot rather than at runtime, the same technique {@code BillingProperties} uses for a
 * negative tax rate: a misconfiguration that stops the application is a bad afternoon, one that
 * silently signs real tokens with a key from a public repository is a breach.
 *
 * <p>Scoped to an explicit profile on purpose. Development and the test suite keep the default and
 * stay frictionless — a security check that makes everyday work painful is one that gets disabled.
 */
@Configuration
@Profile("prod")
public class ProductionSecretCheck implements InitializingBean {

    private final JwtProperties jwtProperties;

    public ProductionSecretCheck(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (JwtProperties.DEV_DEFAULT_SECRET.equals(jwtProperties.secret())) {
            throw new IllegalStateException(
                    "app.security.jwt.secret is still the development default. Set a real secret "
                            + "(JWT_SECRET env var or application-local.yml) before running with "
                            + "the 'prod' profile.");
        }
    }
}

package com.restaurant.security;

import com.restaurant.domain.Role;
import com.restaurant.domain.User;
import com.restaurant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the pre-registered staff accounts on startup (SRS §2.3: users are pre-registered,
 * there is no self-signup). Idempotent — each user is created only if absent — so it is safe
 * to run on every boot. Passwords are hashed with the configured {@link PasswordEncoder}.
 *
 * <p><strong>Dev credentials:</strong> username per role below, password {@code password123}.
 * Replace these before any non-local deployment.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("manager", Role.MANAGER, "Default Manager");
        seed("waiter", Role.WAITER, "Default Waiter");
        seed("kitchen", Role.KITCHEN, "Default Kitchen Staff");
        seed("cashier", Role.CASHIER, "Default Cashier");
    }

    private void seed(String username, Role role, String fullName) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User user = new User(username, passwordEncoder.encode(DEFAULT_PASSWORD), role, fullName);
        userRepository.save(user);
        log.info("Seeded default {} account: username='{}'", role, username);
    }
}

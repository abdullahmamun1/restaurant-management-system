package com.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Dine-In Restaurant Ordering and Inventory System backend.
 *
 * <p>The application follows a layered architecture (see package structure):
 * {@code controller} (presentation/REST) &rarr; {@code service} (business logic) &rarr;
 * {@code repository} (persistence). Domain entities live in {@code domain} and
 * cross-cutting configuration in {@code config}.
 */
@SpringBootApplication
public class RestaurantSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantSystemApplication.class, args);
    }
}

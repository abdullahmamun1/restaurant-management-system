package com.restaurant.controller;

import com.restaurant.controller.dto.LoginRequest;
import com.restaurant.controller.dto.LoginResponse;
import com.restaurant.controller.dto.UserDto;
import com.restaurant.service.AuthService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (FR-01). Thin controller: validates input and delegates to
 * {@link AuthService}; no business logic here.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public: exchange credentials for a JWT bearer token. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Authenticated: return the current user's profile (used to restore a session). */
    @GetMapping("/me")
    public UserDto me(Principal principal) {
        return authService.currentUser(principal.getName());
    }
}

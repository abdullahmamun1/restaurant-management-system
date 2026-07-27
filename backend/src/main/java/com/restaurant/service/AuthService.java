package com.restaurant.service;

import com.restaurant.controller.dto.LoginRequest;
import com.restaurant.controller.dto.LoginResponse;
import com.restaurant.controller.dto.UserDto;
import com.restaurant.domain.User;
import com.restaurant.repository.UserRepository;
import com.restaurant.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use cases (FR-01). Verifies credentials via Spring's
 * {@link AuthenticationManager} (which delegates to our user-details service + BCrypt encoder),
 * then issues a JWT. Business logic lives here, not in the controller (SRP / layering).
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    /**
     * Authenticates the credentials and returns a bearer token on success.
     * Throws {@link org.springframework.security.core.AuthenticationException} on bad credentials.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UsernameNotFoundException(request.username()));

        JwtService.IssuedToken issued = jwtService.issue(user.getUsername(), user.getRole());
        return LoginResponse.bearer(
                issued.token(), issued.expiresAt(),
                user.getUsername(), user.getRole(), user.getFullName());
    }

    /** Returns the profile of the currently authenticated user (for GET /auth/me). */
    @Transactional(readOnly = true)
    public UserDto currentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return userMapper.toDto(user);
    }
}

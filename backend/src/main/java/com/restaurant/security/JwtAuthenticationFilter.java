package com.restaurant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once per request: if a valid {@code Authorization: Bearer <jwt>} header is present, it
 * authenticates the request and populates the {@link SecurityContextHolder}. Requests without a
 * valid token proceed unauthenticated and are rejected by the authorization rules, so RBAC is
 * enforced server-side regardless of the client (NFR-03).
 *
 * <p><strong>The token proves identity; the database decides authority.</strong> The username is
 * taken from the signed claims, but the role and the account's enabled state are read from the
 * user record on every request (M8 D1). Both change while a token is still valid, and a bearer
 * token cannot be recalled once issued:
 *
 * <ul>
 *   <li>Before this, a manager retiring an account revoked nothing until the token expired — up to
 *       a full eight-hour shift — because {@code enabled} was only ever consulted at login. The
 *       User Management screen offered a disable button that did not disable anything until the
 *       next morning.</li>
 *   <li>Likewise a waiter demoted to KITCHEN kept waiter privileges for the rest of the token's
 *       life, because the role travelled in the claims.</li>
 * </ul>
 *
 * <p>The cost is one lookup on {@code idx_app_user_username} per authenticated request, on an API
 * where every business call already makes several. That is the right trade for an authorization
 * decision. The alternatives — short lifetimes plus a refresh flow, or a revocation list — are more
 * machinery for less of the problem.
 *
 * <p>A disabled or deleted account leaves the context unauthenticated, so the request falls through
 * to {@code .anyRequest().authenticated()} and returns <strong>401</strong>. The Angular
 * interceptor already treats a 401 as "session over": it clears state and bounces to the login
 * screen, which is exactly right for an account that has just been retired mid-shift.
 *
 * <p>The role claim stays in the token — the client reads it to choose a home screen — it simply
 * stops being the authority on what may be done.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   AppUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                String username = jwtService.username(claims);

                UserDetails user = userDetailsService.loadUserByUsername(username);
                if (user.isEnabled()) {
                    // Authorities come from the record, never from the claim (see class Javadoc).
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, user.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    // Retired mid-session: leave unauthenticated so the request 401s.
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Invalid, expired, or belonging to an account that no longer exists.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}

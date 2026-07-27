package com.restaurant.security;

import com.restaurant.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless JWT bearer tokens (NFR-03). The signed token carries the
 * username as subject and the {@link Role} as a claim, so RBAC decisions need no DB lookup.
 */
@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = properties.expirationMinutes();
    }

    /** Mints a signed token for the given user. Returns the token and its expiry. */
    public IssuedToken issue(String username, Role role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** Parses and verifies a token, returning its claims. Throws if invalid or expired. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String username(Claims claims) {
        return claims.getSubject();
    }

    public Role role(Claims claims) {
        return Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    }

    /** A freshly issued token and the instant it expires. */
    public record IssuedToken(String token, Instant expiresAt) {
    }
}

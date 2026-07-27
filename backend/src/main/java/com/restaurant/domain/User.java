package com.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A pre-registered staff member who can authenticate. Maps to {@code app_user}.
 *
 * <p>The password is stored only as a salted BCrypt hash (NFR-03) — the plaintext is never
 * persisted. Entities are kept out of the REST boundary; controllers exchange DTOs instead.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected User() {
        // Required by JPA.
    }

    public User(String username, String passwordHash, Role role, String fullName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.enabled = true;
    }

    // ---- Mutations (Manager-driven User Management) ------------------------

    /**
     * Updates the profile a manager may edit. The <strong>username is deliberately not editable</strong>:
     * it is what the audit trail and every {@code created_by}-style reference identify a person by,
     * so renaming one would quietly rewrite the meaning of past records.
     */
    public void updateDetails(String fullName, Role role) {
        this.fullName = fullName;
        this.role = role;
    }

    /**
     * Replaces the stored hash. Takes an already-encoded hash — this class never sees a plaintext
     * password and has no encoder dependency, keeping the domain free of Spring Security.
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * Enables or disables sign-in for this account.
     *
     * <p>This is the account lifecycle in place of deletion. A user who has taken an order, cooked
     * a ticket or settled a payment is referenced by rows the system treats as permanent — the
     * inventory audit log most of all — so accounts are retired, never removed. A disabled user is
     * refused at login by {@code AppUserDetailsService}, which reads this flag.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

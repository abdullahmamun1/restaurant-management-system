package com.restaurant.service;

import com.restaurant.controller.dto.CreateUserRequest;
import com.restaurant.controller.dto.UpdateUserRequest;
import com.restaurant.controller.dto.UserDto;
import com.restaurant.domain.Role;
import com.restaurant.domain.User;
import com.restaurant.repository.UserRepository;
import com.restaurant.service.exception.ConflictException;
import com.restaurant.service.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff account management (Manager only, per the SRS §2.1 access matrix).
 *
 * <p>All accounts are <strong>pre-registered by a manager</strong> — there is no self-signup — so
 * every account in the system is created through {@link #create}.
 *
 * <p>Two safety rules live in this class rather than in the client, because they must hold whatever
 * the caller is:
 *
 * <ul>
 *   <li><strong>No self-sabotage.</strong> A manager cannot disable or demote their own account.
 *       Without this, one careless click ends the session that was making the change — and if they
 *       were the only manager, ends administrative access entirely.</li>
 *   <li><strong>Never zero managers.</strong> The last enabled MANAGER cannot be disabled or given
 *       another role. This is the one state the system cannot talk itself out of: there is no
 *       self-signup and no password reset that does not require a manager, so recovering would take
 *       direct database access.</li>
 * </ul>
 *
 * <p><strong>Accounts are retired, never deleted</strong>, and there is deliberately no delete
 * path. A user who has taken an order, cooked a ticket or settled a payment is referenced by rows
 * this system treats as permanent — the inventory audit log above all, which FR-21 and NFR-06
 * require to stay intact and attributable. Disabling revokes access and leaves the history
 * readable, which is what a departed employee's record should do.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAllByOrderByRoleAscUsernameAsc().stream()
                .map(userMapper::toDto)
                .toList();
    }

    /** Pre-registers a staff account — the only way one comes into existence. */
    @Transactional
    public UserDto create(CreateUserRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException(
                    "The username '" + request.username() + "' is already taken.");
        }
        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.role(),
                request.fullName());
        return userMapper.toDto(userRepository.save(user));
    }

    /** Updates a user's display name and role. */
    @Transactional
    public UserDto update(Long id, UpdateUserRequest request, String actingUsername) {
        User user = require(id);

        if (user.getRole() != request.role()) {
            requireNotSelf(user, actingUsername,
                    "You cannot change your own role. Ask another manager to do it.");
            requireNotLastManager(user, "change the role of");
        }

        user.updateDetails(request.fullName(), request.role());
        return userMapper.toDto(user);
    }

    /** Sets a new password. A manager-driven reset, so no current password is required. */
    @Transactional
    public UserDto resetPassword(Long id, String newPassword) {
        User user = require(id);
        user.changePassword(passwordEncoder.encode(newPassword));
        return userMapper.toDto(user);
    }

    /** Enables or disables sign-in — the account lifecycle, in place of deletion. */
    @Transactional
    public UserDto setEnabled(Long id, boolean enabled, String actingUsername) {
        User user = require(id);

        if (!enabled) {
            requireNotSelf(user, actingUsername,
                    "You cannot disable your own account — you would be signed out of it.");
            requireNotLastManager(user, "disable");
        }

        user.setEnabled(enabled);
        return userMapper.toDto(user);
    }

    // ---- guards -------------------------------------------------------------

    private void requireNotSelf(User target, String actingUsername, String message) {
        if (target.getUsername().equals(actingUsername)) {
            throw new ConflictException(message);
        }
    }

    /**
     * Refuses a change that would leave the system with no enabled manager. Only trips when the
     * target is currently an enabled MANAGER and is the last one, so it never gets in the way of
     * ordinary edits.
     */
    private void requireNotLastManager(User target, String action) {
        if (target.getRole() != Role.MANAGER || !target.isEnabled()) {
            return;
        }
        if (userRepository.countByRoleAndEnabledTrue(Role.MANAGER) <= 1) {
            throw new ConflictException(
                    "You cannot " + action + " the only active manager — that would leave nobody "
                            + "able to manage staff, the menu or inventory. Add another manager "
                            + "first.");
        }
    }

    private User require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found."));
    }
}

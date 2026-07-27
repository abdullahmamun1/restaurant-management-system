package com.restaurant.controller;

import com.restaurant.controller.dto.CreateUserRequest;
import com.restaurant.controller.dto.ResetPasswordRequest;
import com.restaurant.controller.dto.UpdateUserRequest;
import com.restaurant.controller.dto.UserDto;
import com.restaurant.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * User Management (Manager only, per the SRS §2.1 access matrix). Thin controller — delegates to
 * {@link UserService}.
 *
 * <p>The guard sits at <em>class</em> level: no other role has any access to this module at all, so
 * putting the rule on the class means a later endpoint cannot forget it — the same reasoning as
 * {@code InventoryController}, {@code KitchenController} and {@code ReportsController}.
 *
 * <p>Enable and disable are <strong>separate action endpoints</strong> rather than one
 * {@code PATCH {enabled}}, matching how the kitchen's transitions and the payment are expressed: the
 * URL says what happened, which is what an account-status change should leave behind.
 *
 * <p>There is <strong>no delete endpoint</strong>, deliberately — see {@link UserService}. Staff are
 * retired by disabling them, because their name is attached to orders, tickets and audit records
 * that must stay attributable.
 */
@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('MANAGER')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.findAll();
    }

    /** Pre-registers a staff account. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    /** Updates a user's display name and role. */
    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id,
                          @Valid @RequestBody UpdateUserRequest request,
                          Principal principal) {
        return userService.update(id, request, principal.getName());
    }

    /** Sets a new password for an account a manager is resetting. */
    @PostMapping("/{id}/password")
    public UserDto resetPassword(@PathVariable Long id,
                                 @Valid @RequestBody ResetPasswordRequest request) {
        return userService.resetPassword(id, request.password());
    }

    /** Restores sign-in for a retired account. */
    @PostMapping("/{id}/enable")
    public UserDto enable(@PathVariable Long id, Principal principal) {
        return userService.setEnabled(id, true, principal.getName());
    }

    /** Retires an account: sign-in is refused, history stays intact. */
    @PostMapping("/{id}/disable")
    public UserDto disable(@PathVariable Long id, Principal principal) {
        return userService.setEnabled(id, false, principal.getName());
    }
}

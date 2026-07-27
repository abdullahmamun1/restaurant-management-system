package com.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.controller.dto.UpdateUserRequest;
import com.restaurant.domain.Role;
import com.restaurant.domain.User;
import com.restaurant.repository.UserRepository;
import com.restaurant.service.exception.ConflictException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The two guards that stop a manager locking everyone out of the system, driven directly against
 * {@link UserService} with a stubbed repository.
 *
 * <p><strong>Why this exists as a unit test and not an API test (M8 D7).</strong> The
 * last-active-manager guard is <em>unreachable through the API as it stands</em>: any manager
 * making the call must themselves be an enabled manager, so there are always at least two, and the
 * self-guard fires first. That does not make the rule wrong — it becomes reachable the moment
 * anything acts on users other than a signed-in manager (a seeding routine, an ops script, a future
 * admin role) — but it does mean it can only be exercised here, with the repository lying about how
 * many managers there are.
 *
 * <p>The rule that follows from that: an unreachable guard may stay in the code, but it must never
 * be reported as a <em>verified live behaviour</em>. This test verifies the logic; it does not
 * claim the API can trigger it.
 */
class UserServiceGuardsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(users, new UserMapper(), encoder);

    private User user(long id, String username, Role role, boolean enabled) {
        User u = new User(username, "hash", role, "Full Name");
        // The entity has no id setter (ids come from the database), so reflect one in for the test.
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        if (!enabled) {
            u.setEnabled(false);
        }
        return u;
    }

    // ---- The self guard (reachable, and the one that fires in practice) ----

    @Test
    @DisplayName("a manager cannot disable their own account")
    void cannotDisableSelf() {
        User me = user(1L, "manager", Role.MANAGER, true);
        when(users.findById(1L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.setEnabled(1L, false, "manager"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("your own account");

        assertThat(me.isEnabled()).as("the refusal must leave the account untouched").isTrue();
    }

    @Test
    @DisplayName("a manager cannot change their own role")
    void cannotDemoteSelf() {
        User me = user(1L, "manager", Role.MANAGER, true);
        when(users.findById(1L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() ->
                service.update(1L, new UpdateUserRequest("Full Name", Role.WAITER), "manager"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("your own role");

        assertThat(me.getRole()).isEqualTo(Role.MANAGER);
    }

    @Test
    @DisplayName("editing your own NAME is fine — only a role change is guarded")
    void canRenameSelf() {
        User me = user(1L, "manager", Role.MANAGER, true);
        when(users.findById(1L)).thenReturn(Optional.of(me));

        assertThatCode(() ->
                service.update(1L, new UpdateUserRequest("New Name", Role.MANAGER), "manager"))
                .doesNotThrowAnyException();

        assertThat(me.getFullName()).isEqualTo("New Name");
    }

    // ---- The last-manager guard (unreachable via the API — see class Javadoc) ----

    @Test
    @DisplayName("the last active manager cannot be disabled")
    void cannotDisableTheLastManager() {
        User onlyManager = user(2L, "boss", Role.MANAGER, true);
        when(users.findById(2L)).thenReturn(Optional.of(onlyManager));
        when(users.countByRoleAndEnabledTrue(Role.MANAGER)).thenReturn(1L);

        assertThatThrownBy(() -> service.setEnabled(2L, false, "someone-else"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only active manager");

        assertThat(onlyManager.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("the last active manager cannot be given another role")
    void cannotDemoteTheLastManager() {
        User onlyManager = user(2L, "boss", Role.MANAGER, true);
        when(users.findById(2L)).thenReturn(Optional.of(onlyManager));
        when(users.countByRoleAndEnabledTrue(Role.MANAGER)).thenReturn(1L);

        assertThatThrownBy(() ->
                service.update(2L, new UpdateUserRequest("Full Name", Role.CASHIER), "someone-else"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only active manager");

        assertThat(onlyManager.getRole()).isEqualTo(Role.MANAGER);
    }

    @Test
    @DisplayName("with a second active manager the guard stands down")
    void twoManagersMayBeReducedToOne() {
        User other = user(2L, "boss", Role.MANAGER, true);
        when(users.findById(2L)).thenReturn(Optional.of(other));
        when(users.countByRoleAndEnabledTrue(Role.MANAGER)).thenReturn(2L);

        assertThatCode(() -> service.setEnabled(2L, false, "manager"))
                .doesNotThrowAnyException();

        assertThat(other.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("an already-disabled manager is not counted, so the guard does not block")
    void disabledManagerIsNotTheLastActiveOne() {
        User retired = user(2L, "boss", Role.MANAGER, false);
        when(users.findById(2L)).thenReturn(Optional.of(retired));

        assertThatCode(() ->
                service.update(2L, new UpdateUserRequest("Full Name", Role.WAITER), "manager"))
                .doesNotThrowAnyException();

        assertThat(retired.getRole()).isEqualTo(Role.WAITER);
        verify(users, never()).countByRoleAndEnabledTrue(any());
    }

    // ---- Creation ----------------------------------------------------------

    @Test
    @DisplayName("a duplicate username is refused before anything is written")
    void duplicateUsernameIsRefused() {
        when(users.existsByUsernameIgnoreCase("waiter")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new com.restaurant.controller.dto.CreateUserRequest(
                        "waiter", "password123", Role.WAITER, "Dupe")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already taken");

        verify(users, never()).save(any());
    }
}

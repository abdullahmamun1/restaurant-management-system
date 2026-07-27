package com.restaurant.repository;

import com.restaurant.domain.Role;
import com.restaurant.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link User} (Repository pattern). Services depend on this
 * interface, never on a concrete persistence implementation (DIP).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByOrderByRoleAscUsernameAsc();

    /**
     * How many enabled accounts hold a role. Used to refuse the change that would leave the system
     * with no way in: disabling or demoting the last enabled MANAGER (see {@code UserService}).
     */
    long countByRoleAndEnabledTrue(Role role);
}

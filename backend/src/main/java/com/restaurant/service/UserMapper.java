package com.restaurant.service;

import com.restaurant.controller.dto.UserDto;
import com.restaurant.domain.User;
import org.springframework.stereotype.Component;

/**
 * Maps {@link User} entities to boundary DTOs (DTO + Mapper pattern), keeping entities from
 * leaking past the service layer and ensuring sensitive fields (password hash) are never
 * serialized.
 */
@Component
public class UserMapper {

    public UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getFullName(),
                user.isEnabled());
    }
}

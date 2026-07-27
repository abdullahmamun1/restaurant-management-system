package com.restaurant.service;

import com.restaurant.controller.dto.CategoryDto;
import com.restaurant.controller.dto.MenuItemDto;
import com.restaurant.domain.MenuCategory;
import com.restaurant.domain.MenuItem;
import org.springframework.stereotype.Component;

/**
 * Maps menu entities to boundary DTOs (DTO + Mapper pattern), keeping entities out of the
 * REST layer.
 */
@Component
public class MenuMapper {

    public CategoryDto toDto(MenuCategory category) {
        return new CategoryDto(category.getId(), category.getName(), category.getSortOrder());
    }

    public MenuItemDto toDto(MenuItem item) {
        MenuCategory category = item.getCategory();
        return new MenuItemDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                category.getId(),
                category.getName(),
                item.isAvailable());
    }
}

package com.restaurant.controller;

import com.restaurant.controller.dto.AvailabilityRequest;
import com.restaurant.controller.dto.CategoryDto;
import com.restaurant.controller.dto.CategoryRequest;
import com.restaurant.controller.dto.MenuItemDto;
import com.restaurant.controller.dto.MenuItemRequest;
import com.restaurant.service.MenuService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Menu Management endpoints (FR-03, FR-04). Thin controller — delegates to {@link MenuService}.
 *
 * <p>RBAC per SRS §2.1: Managers have full access; Waiters may read; Kitchen/Cashier have no
 * access. Enforced server-side with {@code @PreAuthorize} (NFR-03).
 */
@RestController
@RequestMapping("/menu")
public class MenuController {

    private static final String CAN_READ = "hasAnyRole('MANAGER','WAITER')";
    private static final String CAN_WRITE = "hasRole('MANAGER')";

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    // ---- Categories --------------------------------------------------------

    @GetMapping("/categories")
    @PreAuthorize(CAN_READ)
    public List<CategoryDto> listCategories() {
        return menuService.listCategories();
    }

    @PostMapping("/categories")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return menuService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(CAN_WRITE)
    public CategoryDto updateCategory(@PathVariable Long id,
                                      @Valid @RequestBody CategoryRequest request) {
        return menuService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
    }

    // ---- Items -------------------------------------------------------------

    @GetMapping("/items")
    @PreAuthorize(CAN_READ)
    public List<MenuItemDto> listItems(@RequestParam(required = false) Long categoryId,
                                       @RequestParam(defaultValue = "false") boolean availableOnly) {
        return menuService.listItems(categoryId, availableOnly);
    }

    @GetMapping("/items/{id}")
    @PreAuthorize(CAN_READ)
    public MenuItemDto getItem(@PathVariable Long id) {
        return menuService.getItem(id);
    }

    @PostMapping("/items")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemDto createItem(@Valid @RequestBody MenuItemRequest request) {
        return menuService.createItem(request);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize(CAN_WRITE)
    public MenuItemDto updateItem(@PathVariable Long id,
                                  @Valid @RequestBody MenuItemRequest request) {
        return menuService.updateItem(id, request);
    }

    @PatchMapping("/items/{id}/availability")
    @PreAuthorize(CAN_WRITE)
    public MenuItemDto setAvailability(@PathVariable Long id,
                                       @Valid @RequestBody AvailabilityRequest request) {
        return menuService.setAvailability(id, request.available());
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        menuService.deleteItem(id);
    }
}

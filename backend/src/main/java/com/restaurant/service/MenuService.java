package com.restaurant.service;

import com.restaurant.controller.dto.CategoryDto;
import com.restaurant.controller.dto.CategoryRequest;
import com.restaurant.controller.dto.MenuItemDto;
import com.restaurant.controller.dto.MenuItemRequest;
import com.restaurant.domain.MenuCategory;
import com.restaurant.domain.MenuItem;
import com.restaurant.repository.MenuCategoryRepository;
import com.restaurant.repository.MenuItemRepository;
import com.restaurant.repository.OrderRepository;
import com.restaurant.service.exception.ConflictException;
import com.restaurant.service.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menu Management business logic (FR-03, FR-04). All rules live here — controllers stay thin.
 * Invariants enforced: unique category names, unique item names within a category, a category
 * cannot be deleted while it has items (blocked, not cascaded), and prices are non-negative
 * (also guarded by bean validation + a DB CHECK).
 */
@Service
public class MenuService {

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final MenuMapper mapper;

    public MenuService(MenuCategoryRepository categoryRepository,
                       MenuItemRepository itemRepository,
                       OrderRepository orderRepository,
                       MenuMapper mapper) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.mapper = mapper;
    }

    // ---- Categories (FR-03) ------------------------------------------------

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A category named '" + request.name() + "' already exists.");
        }
        MenuCategory saved = categoryRepository.save(
                new MenuCategory(request.name(), request.sortOrder()));
        return mapper.toDto(saved);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest request) {
        MenuCategory category = requireCategory(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("A category named '" + request.name() + "' already exists.");
        }
        category.rename(request.name());
        category.setSortOrder(request.sortOrder());
        return mapper.toDto(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        MenuCategory category = requireCategory(id);
        if (itemRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "Category '" + category.getName() + "' still has items. "
                            + "Move or delete its items before deleting the category.");
        }
        categoryRepository.delete(category);
    }

    // ---- Items (FR-04) -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<MenuItemDto> listItems(Long categoryId, boolean availableOnly) {
        List<MenuItem> items;
        if (categoryId != null) {
            items = itemRepository.findByCategoryIdOrderByNameAsc(categoryId);
        } else if (availableOnly) {
            items = itemRepository.findByAvailableTrueOrderByNameAsc();
        } else {
            items = itemRepository.findAll();
        }
        return items.stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MenuItemDto getItem(Long id) {
        return mapper.toDto(requireItem(id));
    }

    @Transactional
    public MenuItemDto createItem(MenuItemRequest request) {
        MenuCategory category = requireCategory(request.categoryId());
        if (itemRepository.existsByCategoryIdAndNameIgnoreCase(category.getId(), request.name())) {
            throw new ConflictException(
                    "An item named '" + request.name() + "' already exists in this category.");
        }
        MenuItem saved = itemRepository.save(new MenuItem(
                request.name(), request.description(), request.price(),
                category, request.available()));
        return mapper.toDto(saved);
    }

    @Transactional
    public MenuItemDto updateItem(Long id, MenuItemRequest request) {
        MenuItem item = requireItem(id);
        MenuCategory category = requireCategory(request.categoryId());
        if (itemRepository.existsByCategoryIdAndNameIgnoreCaseAndIdNot(
                category.getId(), request.name(), id)) {
            throw new ConflictException(
                    "An item named '" + request.name() + "' already exists in this category.");
        }
        item.update(request.name(), request.description(), request.price(),
                category, request.available());
        return mapper.toDto(item);
    }

    @Transactional
    public MenuItemDto setAvailability(Long id, boolean available) {
        MenuItem item = requireItem(id);
        item.setAvailable(available);
        return mapper.toDto(item);
    }

    /**
     * Deletes a menu item, unless it appears on an existing order. Orders snapshot the price but
     * still reference the item, so deleting one would rewrite history (and trip the
     * {@code fk_order_item_menu_item} RESTRICT); the manager is pointed at the availability flag
     * instead, which is what FR-05 is for.
     */
    @Transactional
    public void deleteItem(Long id) {
        MenuItem item = requireItem(id);
        if (orderRepository.isMenuItemOrdered(id)) {
            throw new ConflictException(
                    "'" + item.getName() + "' appears on existing orders and cannot be deleted. "
                            + "Mark it unavailable instead.");
        }
        itemRepository.delete(item);
    }

    /**
     * Availability check reused by the M4 order-item validator to enforce FR-05
     * ("cannot add an unavailable item").
     */
    @Transactional(readOnly = true)
    public boolean isAvailable(Long itemId) {
        return requireItem(itemId).isAvailable();
    }

    // ---- helpers -----------------------------------------------------------

    private MenuCategory requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found."));
    }

    private MenuItem requireItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item " + id + " not found."));
    }
}

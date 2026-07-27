import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import {
  CategoryRequest,
  MenuCategory,
  MenuItem,
  MenuItemRequest,
  Recipe,
  RecipeRequest
} from './menu.models';

/**
 * Client-side gateway to the Menu Management API (FR-03, FR-04). Thin wrapper over
 * {@link ApiService}; components call these Promise-returning methods and manage their own
 * view state.
 */
@Injectable({ providedIn: 'root' })
export class MenuService {
  private readonly api = inject(ApiService);

  // ---- Categories --------------------------------------------------------
  listCategories(): Promise<MenuCategory[]> {
    return firstValueFrom(this.api.get<MenuCategory[]>('/menu/categories'));
  }

  createCategory(body: CategoryRequest): Promise<MenuCategory> {
    return firstValueFrom(this.api.post<MenuCategory>('/menu/categories', body));
  }

  updateCategory(id: number, body: CategoryRequest): Promise<MenuCategory> {
    return firstValueFrom(this.api.put<MenuCategory>(`/menu/categories/${id}`, body));
  }

  deleteCategory(id: number): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`/menu/categories/${id}`));
  }

  // ---- Items -------------------------------------------------------------
  /**
   * @param availableOnly restrict to sellable items — used by the waiter's order builder so
   *   unavailable dishes are not offered. The server still enforces FR-05 on add (FR-08a).
   */
  listItems(availableOnly = false): Promise<MenuItem[]> {
    const query = availableOnly ? '?availableOnly=true' : '';
    return firstValueFrom(this.api.get<MenuItem[]>(`/menu/items${query}`));
  }

  createItem(body: MenuItemRequest): Promise<MenuItem> {
    return firstValueFrom(this.api.post<MenuItem>('/menu/items', body));
  }

  updateItem(id: number, body: MenuItemRequest): Promise<MenuItem> {
    return firstValueFrom(this.api.put<MenuItem>(`/menu/items/${id}`, body));
  }

  setAvailability(id: number, available: boolean): Promise<MenuItem> {
    return firstValueFrom(
      this.api.patch<MenuItem>(`/menu/items/${id}/availability`, { available })
    );
  }

  deleteItem(id: number): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`/menu/items/${id}`));
  }

  // ---- Recipe (maps an item to its ingredients; Manager-only) -------------
  getRecipe(menuItemId: number): Promise<Recipe> {
    return firstValueFrom(this.api.get<Recipe>(`/menu/items/${menuItemId}/recipe`));
  }

  saveRecipe(menuItemId: number, body: RecipeRequest): Promise<Recipe> {
    return firstValueFrom(this.api.put<Recipe>(`/menu/items/${menuItemId}/recipe`, body));
  }
}

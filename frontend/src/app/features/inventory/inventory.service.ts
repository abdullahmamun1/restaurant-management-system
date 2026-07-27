import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import {
  Adjustment,
  AdjustmentRequest,
  Ingredient,
  IngredientCreateRequest,
  IngredientUpdateRequest
} from './inventory.models';

/**
 * Client-side gateway to the Inventory API (FR-17, FR-18, FR-21). Thin wrapper over
 * {@link ApiService}. Stock is never set directly — only via {@link adjust}.
 */
@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly api = inject(ApiService);

  listIngredients(): Promise<Ingredient[]> {
    return firstValueFrom(this.api.get<Ingredient[]>('/inventory/ingredients'));
  }

  /**
   * Ingredients at or below their low-stock threshold (FR-18, FR-20) — the manager dashboard's
   * alert panel as well as this screen's filter.
   *
   * <p>Deliberately a server call rather than filtering {@link listIngredients} on `lowStock`:
   * "at or below threshold" is a business rule, and this client's standing habit is to read
   * derived values from the API rather than re-derive them (the same reason `OrderDto.editable`
   * exists). M7 reuses this instead of adding a `/reports/low-stock` twin (M7 D6).
   */
  listLowStock(): Promise<Ingredient[]> {
    return firstValueFrom(this.api.get<Ingredient[]>('/inventory/ingredients/low-stock'));
  }

  create(body: IngredientCreateRequest): Promise<Ingredient> {
    return firstValueFrom(this.api.post<Ingredient>('/inventory/ingredients', body));
  }

  update(id: number, body: IngredientUpdateRequest): Promise<Ingredient> {
    return firstValueFrom(this.api.put<Ingredient>(`/inventory/ingredients/${id}`, body));
  }

  remove(id: number): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`/inventory/ingredients/${id}`));
  }

  adjust(id: number, body: AdjustmentRequest): Promise<Ingredient> {
    return firstValueFrom(
      this.api.post<Ingredient>(`/inventory/ingredients/${id}/adjustments`, body)
    );
  }

  history(id: number): Promise<Adjustment[]> {
    return firstValueFrom(this.api.get<Adjustment[]>(`/inventory/ingredients/${id}/adjustments`));
  }
}

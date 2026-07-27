import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AddOrderItemRequest, Order, RestaurantTable } from './orders.models';

/**
 * Client-side gateway to the Table & Orders API (FR-06–FR-10). Thin wrapper over
 * {@link ApiService}, mirroring the menu and inventory services.
 *
 * <p>Every mutation resolves to the full {@link Order}, so callers replace their state from the
 * response rather than patching it locally — the subtotal and `editable` flag stay authoritative.
 */
@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly api = inject(ApiService);

  listTables(): Promise<RestaurantTable[]> {
    return firstValueFrom(this.api.get<RestaurantTable[]>('/tables'));
  }

  setServiceFlag(tableId: number, needsService: boolean): Promise<RestaurantTable> {
    return firstValueFrom(
      this.api.patch<RestaurantTable>(`/tables/${tableId}/service-flag`, { needsService })
    );
  }

  getOrder(id: number): Promise<Order> {
    return firstValueFrom(this.api.get<Order>(`/orders/${id}`));
  }

  createOrder(tableId: number): Promise<Order> {
    return firstValueFrom(this.api.post<Order>('/orders', { tableId }));
  }

  addItem(orderId: number, body: AddOrderItemRequest): Promise<Order> {
    return firstValueFrom(this.api.post<Order>(`/orders/${orderId}/items`, body));
  }

  /**
   * Sets a line's quantity outright — the +/- controls on the ticket.
   *
   * An absolute quantity rather than a delta, so several quick taps converge on the number the
   * waiter actually wants however the requests interleave. Dropping to zero is a removal and has
   * its own call; the API rejects a quantity below 1.
   */
  changeItemQuantity(orderId: number, itemId: number, quantity: number): Promise<Order> {
    return firstValueFrom(
      this.api.patch<Order>(`/orders/${orderId}/items/${itemId}`, { quantity })
    );
  }

  removeItem(orderId: number, itemId: number): Promise<Order> {
    return firstValueFrom(this.api.delete<Order>(`/orders/${orderId}/items/${itemId}`));
  }

  confirm(orderId: number): Promise<Order> {
    return firstValueFrom(this.api.post<Order>(`/orders/${orderId}/confirm`, {}));
  }

  serve(orderId: number): Promise<Order> {
    return firstValueFrom(this.api.post<Order>(`/orders/${orderId}/serve`, {}));
  }
}

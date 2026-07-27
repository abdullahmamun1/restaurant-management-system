import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Order } from '../orders/orders.models';
import { KitchenTicket } from './kitchen.models';

/**
 * Client-side gateway to the Kitchen Queue API (FR-11, FR-12), mirroring {@link OrdersService}.
 *
 * <p>The read and the writes sit on different paths for a reason: `/kitchen/queue` is the kitchen's
 * own module (Kitchen-only under SRS §2.1), while `prepare`/`ready` are lifecycle transitions on an
 * order. Both are Kitchen-guarded server-side.
 *
 * <p>The two mutations resolve to the full {@link Order}, not a ticket — they are the order
 * endpoints — so callers use the returned `status` rather than assuming the transition landed.
 */
@Injectable({ providedIn: 'root' })
export class KitchenService {
  private readonly api = inject(ApiService);

  getQueue(): Promise<KitchenTicket[]> {
    return firstValueFrom(this.api.get<KitchenTicket[]>('/kitchen/queue'));
  }

  prepare(orderId: number): Promise<Order> {
    return firstValueFrom(this.api.post<Order>(`/orders/${orderId}/prepare`, {}));
  }

  ready(orderId: number): Promise<Order> {
    return firstValueFrom(this.api.post<Order>(`/orders/${orderId}/ready`, {}));
  }
}

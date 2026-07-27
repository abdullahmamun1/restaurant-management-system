import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Bill, PayableOrder, PaymentMethod, Receipt } from './billing.models';

/**
 * Client-side gateway to the Billing & Payment API (FR-13–FR-16), mirroring the other feature
 * services.
 *
 * <p>Bill and receipt are separate calls with disjoint, status-guarded meanings: a bill exists only
 * while an order is SERVED, a receipt only once it is PAID. The client does not decide which to
 * ask for based on a status it inferred — it asks for the one the screen is for, and a 409 is the
 * server saying the order is not in that state.
 *
 * <p>Every amount that comes back is authoritative. Nothing in this feature recomputes money.
 */
@Injectable({ providedIn: 'root' })
export class BillingService {
  private readonly api = inject(ApiService);

  /** SERVED orders awaiting settlement, longest-served first (FR-13). */
  listPayable(): Promise<PayableOrder[]> {
    return firstValueFrom(this.api.get<PayableOrder[]>('/billing/orders'));
  }

  /** The itemised bill and its total breakdown (FR-13, FR-14). SERVED orders only. */
  getBill(orderId: number): Promise<Bill> {
    return firstValueFrom(this.api.get<Bill>(`/billing/orders/${orderId}/bill`));
  }

  /**
   * Records payment and settles the order (FR-15), returning the receipt (FR-16) — one round-trip,
   * because the order status, the table release and the ingredient deduction (FR-19) all happen in
   * the same server transaction and there is nothing sensible to show in between.
   *
   * <p>A 409 here is a real refusal, not a glitch: either the order is not SERVED (a double-tap, or
   * someone else settled it first) or stock has fallen too far to fulfil it. Both carry a message
   * the cashier needs to read.
   */
  pay(orderId: number, method: PaymentMethod): Promise<Receipt> {
    return firstValueFrom(
      this.api.post<Receipt>(`/billing/orders/${orderId}/payment`, { method })
    );
  }

  /** Re-reads a settled order's receipt (FR-16). Identical every time — the amounts are frozen. */
  getReceipt(orderId: number): Promise<Receipt> {
    return firstValueFrom(this.api.get<Receipt>(`/billing/orders/${orderId}/receipt`));
  }
}

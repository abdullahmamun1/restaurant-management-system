import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { SalesSummary, TopItem } from './reports.models';

/**
 * Client-side gateway to the Manager reporting API (FR-23, FR-24), mirroring the other feature
 * services.
 *
 * <p>There is deliberately **no low-stock method here**. FR-20's panel is served by the existing
 * `InventoryService.listLowStock()` — same query, same Manager-only audience — and the dashboard
 * injects that service directly rather than routing an identical call through a second gateway
 * (M7 D6).
 *
 * <p>Dates go over the wire as plain `YYYY-MM-DD`; the server resolves them against the
 * restaurant's timezone and echoes back which one it used. Nothing here converts or recomputes an
 * amount — every figure the API returns is authoritative and already rounded to 2dp.
 */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly api = inject(ApiService);

  /** Total revenue, orders completed and the per-category breakdown for a range (FR-23). */
  salesSummary(from: string, to: string): Promise<SalesSummary> {
    return firstValueFrom(
      this.api.get<SalesSummary>(`/reports/sales?from=${from}&to=${to}`)
    );
  }

  /** The best-selling menu items in a range, by quantity sold (FR-24). */
  topItems(from: string, to: string, limit = 10): Promise<TopItem[]> {
    return firstValueFrom(
      this.api.get<TopItem[]>(`/reports/top-items?from=${from}&to=${to}&limit=${limit}`)
    );
  }
}

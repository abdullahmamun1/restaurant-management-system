import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ArrowLeft, Check, RefreshCw } from '../../core/icons';
import { Ingredient } from '../inventory/inventory.models';
import { InventoryService } from '../inventory/inventory.service';
import { ReportsService } from './reports.service';
import { RangePreset, SalesSummary, TopItem } from './reports.models';

/**
 * Manager dashboard — Reports & Alerts (FR-20, FR-23, FR-24).
 *
 * <p>Three panels, one shared date range — with one deliberate exception: **low stock is not behind
 * the range picker**. FR-20 is "currently at or below threshold", a question with no date
 * dimension, and putting it behind a range would imply a historical view that does not exist. It
 * loads once and refreshes on demand.
 *
 * <p>Each panel owns its own loading and error state rather than the page owning one for all three.
 * Three independent calls means a failing sales query must not blank the low-stock alerts — those
 * are the ones a manager acts on soonest.
 *
 * <p>The low-stock panel uses {@link InventoryService} directly: FR-20 is already served by
 * `/inventory/ingredients/low-stock`, and a `/reports/low-stock` twin would be a second endpoint to
 * keep in sync for no gain (M7 D6).
 */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.css'
})
export class ReportsComponent implements OnInit {
  readonly icons = { back: ArrowLeft, ok: Check, refresh: RefreshCw };

  private readonly reports = inject(ReportsService);
  private readonly inventory = inject(InventoryService);

  // ---- Range (shared by the two date-scoped panels) ----------------------
  readonly from = signal(ReportsComponent.today());
  readonly to = signal(ReportsComponent.today());
  readonly preset = signal<RangePreset>('today');

  // ---- Sales (FR-23) ------------------------------------------------------
  readonly sales = signal<SalesSummary | null>(null);
  readonly salesLoading = signal(true);
  readonly salesError = signal<string | null>(null);

  // ---- Top items (FR-24) --------------------------------------------------
  readonly topItems = signal<TopItem[]>([]);
  readonly topLoading = signal(true);
  readonly topError = signal<string | null>(null);

  // ---- Low stock (FR-20) --------------------------------------------------
  readonly lowStock = signal<Ingredient[]>([]);
  readonly lowLoading = signal(true);
  readonly lowError = signal<string | null>(null);

  /** The busiest item's quantity — the scale every top-item bar is drawn against. */
  readonly topMax = computed(() =>
    this.topItems().reduce((max, i) => Math.max(max, i.quantitySold), 0)
  );

  readonly hasSales = computed(() => (this.sales()?.ordersCompleted ?? 0) > 0);

  ngOnInit(): void {
    void this.reloadRange();
    void this.reloadLowStock();
  }

  // ---- Range control ------------------------------------------------------

  /** Presets exist so the common questions are one tap, not two date pickers. */
  applyPreset(preset: RangePreset): void {
    const today = new Date();
    let start = new Date(today);

    if (preset === 'week') {
      start.setDate(today.getDate() - 6); // today inclusive, so 6 back = 7 days
    } else if (preset === 'month') {
      start = new Date(today.getFullYear(), today.getMonth(), 1);
    }

    this.preset.set(preset);
    this.from.set(ReportsComponent.iso(start));
    this.to.set(ReportsComponent.iso(today));
    void this.reloadRange();
  }

  onFromChange(value: string): void {
    this.from.set(value);
    this.preset.set('custom');
  }

  onToChange(value: string): void {
    this.to.set(value);
    this.preset.set('custom');
  }

  /**
   * Reloads both date-scoped panels. The server refuses a reversed range with a 400 — the client
   * does not pre-empt that check, so there is one authority on what a valid range is.
   */
  async reloadRange(): Promise<void> {
    await Promise.all([this.loadSales(), this.loadTopItems()]);
  }

  private async loadSales(): Promise<void> {
    this.salesLoading.set(true);
    this.salesError.set(null);
    try {
      this.sales.set(await this.reports.salesSummary(this.from(), this.to()));
    } catch (e) {
      this.sales.set(null);
      this.salesError.set(this.message(e));
    } finally {
      this.salesLoading.set(false);
    }
  }

  private async loadTopItems(): Promise<void> {
    this.topLoading.set(true);
    this.topError.set(null);
    try {
      this.topItems.set(await this.reports.topItems(this.from(), this.to()));
    } catch (e) {
      this.topItems.set([]);
      this.topError.set(this.message(e));
    } finally {
      this.topLoading.set(false);
    }
  }

  async reloadLowStock(): Promise<void> {
    this.lowLoading.set(true);
    this.lowError.set(null);
    try {
      this.lowStock.set(await this.inventory.listLowStock());
    } catch (e) {
      this.lowStock.set([]);
      this.lowError.set(this.message(e));
    } finally {
      this.lowLoading.set(false);
    }
  }

  // ---- Presentation helpers ----------------------------------------------

  /** Bar width for a top-item row, as a percentage of the busiest seller. */
  barWidth(item: TopItem): number {
    const max = this.topMax();
    return max === 0 ? 0 : (item.quantitySold / max) * 100;
  }

  money(amount: number): string {
    return amount.toFixed(2);
  }

  /**
   * A share as a fixed 1dp percentage. The server sends 1dp, but JSON has no decimal type, so
   * `57.0` parses to the JS number `57` and would render as "57%" beside "38.4%". Formatting is
   * the client's job; the value still comes from the server (M7 D3).
   */
  pct(share: number): string {
    return share.toFixed(1);
  }

  private static today(): string {
    return ReportsComponent.iso(new Date());
  }

  /** Local calendar date as YYYY-MM-DD — never `toISOString()`, which shifts to UTC first. */
  private static iso(date: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  private message(e: unknown): string {
    return (e as { error?: { detail?: string } })?.error?.detail
      ?? 'Something went wrong. Please try again.';
  }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ArrowLeft, Check, CircleAlert, Flag } from '../../core/icons';
import { AuthService } from '../../core/auth.service';
import { OrdersService } from './orders.service';
import { RestaurantTable } from './orders.models';

/**
 * The dining-room floor (FR-06). Tapping a free table opens an order on it and goes straight to
 * the builder — two taps from login to ordering (NFR-01). Tapping an occupied table opens the
 * order already on it.
 *
 * <p>Write actions are shown only to the Waiter, who owns Table & Orders per SRS §2.1; Manager,
 * Kitchen and Cashier get the same read-only floor view. That gating is cosmetic — the server
 * enforces it independently (NFR-03).
 */
@Component({
  selector: 'app-tables',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './tables.component.html',
  styleUrl: './tables.component.css'
})
export class TablesComponent implements OnInit {
  readonly icons = { back: ArrowLeft, ok: Check, alert: CircleAlert, flag: Flag };

  private readonly orders = inject(OrdersService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly tables = signal<RestaurantTable[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** Id of the table currently being acted on, so only its card shows a busy state. */
  readonly busyTableId = signal<number | null>(null);

  readonly canEdit = computed(() => this.auth.role() === 'WAITER');
  readonly openCount = computed(
    () => this.tables().filter((t) => t.status !== 'AVAILABLE').length
  );

  ngOnInit(): void {
    this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    try {
      this.tables.set(await this.orders.listTables());
      this.error.set(null);
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** Free table → open an order and jump to it. Occupied → open the order already there. */
  async openTable(table: RestaurantTable): Promise<void> {
    if (table.activeOrderId) {
      this.router.navigate(['/orders', table.activeOrderId]);
      return;
    }
    if (!this.canEdit()) {
      return;
    }
    this.busyTableId.set(table.id);
    this.error.set(null);
    try {
      const order = await this.orders.createOrder(table.id);
      this.router.navigate(['/orders', order.id]);
    } catch (e) {
      this.error.set(this.message(e));
      await this.reload();
    } finally {
      this.busyTableId.set(null);
    }
  }

  async toggleServiceFlag(table: RestaurantTable, event: Event): Promise<void> {
    event.stopPropagation(); // the card itself opens the order
    this.busyTableId.set(table.id);
    this.error.set(null);
    try {
      const updated = await this.orders.setServiceFlag(
        table.id,
        table.status !== 'NEEDS_SERVICE'
      );
      this.tables.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.busyTableId.set(null);
    }
  }

  private message(e: unknown): string {
    return (e as { error?: { detail?: string } })?.error?.detail
      ?? 'Something went wrong. Please try again.';
  }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../core/auth.service';
import { ArrowLeft, Check, CircleAlert, Lock, Minus, Plus, Trash2 } from '../../core/icons';
import { MenuService } from '../menu/menu.service';
import { MenuCategory, MenuItem } from '../menu/menu.models';
import { OrdersService } from './orders.service';
import { Order, OrderItem } from './orders.models';

interface PickerGroup {
  category: MenuCategory;
  items: MenuItem[];
}

/**
 * The waiter's order builder (FR-07–FR-10): pick items with quantity and notes, watch the running
 * subtotal, then confirm to the kitchen and mark served.
 *
 * <p>Two rules are deliberately not re-implemented here. Whether items may still be edited comes
 * from the server's `editable` flag (FR-07/FR-09), and whether an item may be added at all is the
 * server's answer too (FR-08) — a rejection is surfaced verbatim rather than pre-empted, because
 * stock can change between loading the menu and adding a dish.
 */
@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.css'
})
export class OrderDetailComponent implements OnInit {
  private readonly orders = inject(OrdersService);
  private readonly menu = inject(MenuService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly icons = {
    back: ArrowLeft,
    plus: Plus,
    minus: Minus,
    remove: Trash2,
    locked: Lock,
    ok: Check,
    alert: CircleAlert
  };

  readonly order = signal<Order | null>(null);
  readonly groups = signal<PickerGroup[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  /** Per-item draft quantity and notes in the picker, keyed by menu-item id. */
  readonly draftQty = signal<Record<number, number>>({});
  readonly draftNotes = signal<Record<number, string>>({});

  private readonly isWaiter = computed(() => this.auth.role() === 'WAITER');

  /** Item edits need both the role (SRS §2.1) and the order's own edit window (FR-07). */
  readonly canEditItems = computed(() => this.isWaiter() && (this.order()?.editable ?? false));
  readonly canConfirm = computed(() => {
    const o = this.order();
    return this.isWaiter() && !!o && o.editable && o.items.length > 0;
  });
  readonly canServe = computed(() => this.isWaiter() && this.order()?.status === 'READY');
  readonly itemCount = computed(
    () => this.order()?.items.reduce((sum, line) => sum + line.quantity, 0) ?? 0
  );

  async ngOnInit(): Promise<void> {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    try {
      const [order, items, categories] = await Promise.all([
        this.orders.getOrder(id),
        this.menu.listItems(true),
        this.menu.listCategories()
      ]);
      this.order.set(order);
      this.groups.set(this.groupByCategory(items, categories));
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.loading.set(false);
    }
  }

  qtyFor(itemId: number): number {
    return this.draftQty()[itemId] ?? 1;
  }

  notesFor(itemId: number): string {
    return this.draftNotes()[itemId] ?? '';
  }

  setQty(itemId: number, raw: string): void {
    const parsed = Number(raw);
    const qty = Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1;
    this.draftQty.update((m) => ({ ...m, [itemId]: qty }));
  }

  setNotes(itemId: number, value: string): void {
    this.draftNotes.update((m) => ({ ...m, [itemId]: value }));
  }

  async add(item: MenuItem): Promise<void> {
    const order = this.order();
    if (!order) return;
    const notes = this.notesFor(item.id).trim();
    await this.run(async () => {
      const updated = await this.orders.addItem(order.id, {
        menuItemId: item.id,
        quantity: this.qtyFor(item.id),
        notes: notes.length > 0 ? notes : null
      });
      this.order.set(updated);
      // Reset this item's draft so the next tap starts from a clean slate.
      this.draftQty.update((m) => ({ ...m, [item.id]: 1 }));
      this.draftNotes.update((m) => ({ ...m, [item.id]: '' }));
    });
  }

  /**
   * Steps a line's quantity by one.
   *
   * <p>Stepping below 1 is a removal, so it is routed to the delete call rather than sent as
   * `quantity: 0` — the API refuses that, deliberately, because "none of this" and "this many of
   * this" are different requests. The minus button on a single-quantity line therefore clears it,
   * which is what a waiter expects it to do.
   *
   * <p>The quantity sent is absolute, so a burst of taps lands on the right total regardless of
   * the order the responses come back in.
   */
  async step(line: OrderItem, by: 1 | -1): Promise<void> {
    const order = this.order();
    if (!order || this.busy()) return;

    const next = line.quantity + by;
    await this.run(async () => {
      this.order.set(
        next < 1
          ? await this.orders.removeItem(order.id, line.id)
          : await this.orders.changeItemQuantity(order.id, line.id, next)
      );
    });
  }

  async remove(itemId: number): Promise<void> {
    const order = this.order();
    if (!order) return;
    await this.run(async () => {
      this.order.set(await this.orders.removeItem(order.id, itemId));
    });
  }

  async confirm(): Promise<void> {
    const order = this.order();
    if (!order) return;
    await this.run(async () => {
      this.order.set(await this.orders.confirm(order.id));
      this.notice.set('Sent to the kitchen.');
    });
  }

  async serve(): Promise<void> {
    const order = this.order();
    if (!order) return;
    await this.run(async () => {
      this.order.set(await this.orders.serve(order.id));
      this.notice.set('Marked as served.');
    });
  }

  private groupByCategory(items: MenuItem[], categories: MenuCategory[]): PickerGroup[] {
    return categories
      .map((category) => ({
        category,
        items: items.filter((i) => i.categoryId === category.id)
      }))
      .filter((group) => group.items.length > 0);
  }

  private async run(fn: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    try {
      await fn();
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.busy.set(false);
    }
  }

  private message(e: unknown): string {
    return (e as { error?: { detail?: string } })?.error?.detail
      ?? 'Something went wrong. Please try again.';
  }
}

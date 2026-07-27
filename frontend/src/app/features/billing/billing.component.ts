import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ArrowLeft, Ban, Check, CircleAlert } from '../../core/icons';
import { AuthService } from '../../core/auth.service';
import { BillingService } from './billing.service';
import { Bill, PayableOrder, PaymentMethod } from './billing.models';

/**
 * The till (FR-13–FR-15): the list of served tables waiting to settle, the bill for the one that is
 * open, and the confirmation that takes the money.
 *
 * <p><strong>Three steps, and no more</strong> (NFR-01): pick the table, pick the method, confirm.
 * The bill opens on the same screen rather than on a route of its own, so the cashier never
 * navigates mid-settlement; the receipt is where they land afterwards, and it is a result rather
 * than a fourth step.
 *
 * <p><strong>The confirm button is the only irreversible control in this application.</strong>
 * There are no refunds, voids or cancellations anywhere in the system — once it is pressed the
 * order is closed for good. It therefore carries the amount as well as the action ("Take 48.30 ·
 * Cash"), so what is confirmed is the figure and not merely the gesture, and it disables the
 * instant it is pressed. The server returns 409 on a second attempt regardless; disabling is about
 * not showing the cashier an error for a slow network.
 *
 * <p><strong>Read-only for a Manager.</strong> SRS §2.1 gives Manager Read on this module, so the
 * method buttons and the confirm do not render for them. That is a courtesy to the UI — the server
 * enforces it (NFR-03).
 */
@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './billing.component.html',
  styleUrl: './billing.component.css'
})
export class BillingComponent implements OnInit {
  readonly icons = { back: ArrowLeft, blocked: Ban, ok: Check, alert: CircleAlert };

  /** The three methods the SRS recognises, as buttons: one tap, thumb-reachable on a tablet. */
  readonly methods: readonly PaymentMethod[] = ['CASH', 'CARD', 'MOBILE'];

  private readonly billing = inject(BillingService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly payable = signal<PayableOrder[]>([]);
  readonly bill = signal<Bill | null>(null);
  readonly loading = signal(true);
  /** A bill is being fetched — the worklist stays interactive, the panel shows its own state. */
  readonly opening = signal(false);
  /** The payment itself is in flight; the confirm is dead from the first press until it resolves. */
  readonly settling = signal(false);
  readonly method = signal<PaymentMethod | null>(null);

  /**
   * A refusal the cashier has to act on, kept apart from `notice` because it is not dismissible
   * chrome — see {@link shortfall}.
   */
  readonly problem = signal<string | null>(null);
  /** True when `problem` is a stock shortfall, which needs a manager rather than a retry. */
  readonly needsRestock = signal(false);
  readonly notice = signal<string | null>(null);

  readonly canSettle = computed(() => this.auth.role() === 'CASHIER');
  readonly waitingCount = computed(() => this.payable().length);
  readonly canConfirm = computed(
    () => this.canSettle() && !!this.bill() && !!this.method() && !this.settling()
  );

  async ngOnInit(): Promise<void> {
    await this.refresh();
    this.loading.set(false);
  }

  /** Re-reads the worklist from the server — the authority on what is still unsettled. */
  async refresh(): Promise<void> {
    try {
      this.payable.set(await this.billing.listPayable());
    } catch (e) {
      this.problem.set(this.message(e));
    }
  }

  /** Step one: open a table's bill (FR-13, FR-14). */
  async open(order: PayableOrder): Promise<void> {
    this.opening.set(true);
    this.clearMessages();
    this.method.set(null);
    try {
      this.bill.set(await this.billing.getBill(order.orderId));
    } catch (e) {
      this.bill.set(null);
      // 409 means the order is no longer SERVED — somebody settled it while this list was on
      // screen. Re-reading is the honest fix; the row simply disappears.
      this.problem.set(this.message(e));
      await this.refresh();
    } finally {
      this.opening.set(false);
    }
  }

  /** Step two: choose how the money arrives. */
  choose(method: PaymentMethod): void {
    this.method.set(method);
    this.clearMessages();
  }

  close(): void {
    this.bill.set(null);
    this.method.set(null);
    this.clearMessages();
  }

  /**
   * Step three, and the point of no return (FR-15): records the payment, marks the order PAID,
   * frees the table and deducts the ingredients — one server transaction, all or nothing.
   *
   * <p>On success the receipt is the destination, so a reload or a later visit to that URL prints
   * the same receipt (FR-16). On a 409 nothing happened at all: the order is still served, the
   * table still occupied, and no stock moved.
   */
  async confirm(): Promise<void> {
    const bill = this.bill();
    const method = this.method();
    if (!bill || !method || this.settling()) {
      return;
    }

    this.settling.set(true);
    this.clearMessages();
    try {
      const receipt = await this.billing.pay(bill.orderId, method);
      await this.router.navigate(['/billing/receipt', receipt.orderId]);
    } catch (e) {
      const detail = this.message(e);
      this.problem.set(detail);
      // A stock shortfall is the one error in this application the user cannot resolve alone:
      // refunds and cancellation are out of scope, so the only way forward is a manager restock.
      // Flagged so the panel can say that rather than showing a bare red banner.
      this.needsRestock.set(this.isShortfall(e));
      this.settling.set(false);
      await this.refresh();
    }
  }

  /** Minutes a table has been waiting to pay — what a cashier triages on. */
  waitingLabel(order: PayableOrder): string {
    const minutes = Math.max(0, Math.round((Date.now() - Date.parse(order.servedAt)) / 60000));
    if (minutes < 1) {
      return 'just served';
    }
    return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
  }

  private clearMessages(): void {
    this.problem.set(null);
    this.needsRestock.set(false);
    this.notice.set(null);
  }

  /**
   * Distinguishes the FR-22 stock halt from every other 409 (a double-tap, an order someone else
   * already settled). Keyed off the server's `reason` marker rather than the wording of `detail`:
   * matching the prose was tried first and broke immediately, because the message a cashier sees
   * is not the message the domain layer writes.
   */
  private isShortfall(e: unknown): boolean {
    const err = e as { status?: number; error?: { reason?: string } };
    return err?.status === 409 && err?.error?.reason === 'INSUFFICIENT_STOCK';
  }

  private message(e: unknown): string {
    return (
      (e as { error?: { detail?: string } })?.error?.detail ??
      'Something went wrong. Please try again.'
    );
  }
}

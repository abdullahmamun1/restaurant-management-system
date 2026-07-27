import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ArrowLeft, Check } from '../../core/icons';
import { BillingService } from './billing.service';
import { Receipt } from './billing.models';

/**
 * The receipt for a settled order (FR-16), on a route of its own.
 *
 * <p>Addressable rather than a panel on the till, for one practical reason: a receipt has to be
 * re-retrievable. Its amounts are the snapshot frozen at payment, so this URL prints the same
 * document a week later whatever the configured tax rate has become — which is what a cashier who
 * closed the tab mid-shift, or a guest who asks again on the way out, actually needs.
 *
 * <p>It reads what it needs from the server rather than being handed state by the till, so a
 * reload works and so does a link.
 */
@Component({
  selector: 'app-receipt',
  standalone: true,
  imports: [RouterLink, DatePipe, LucideAngularModule],
  templateUrl: './receipt.component.html',
  styleUrl: './receipt.component.css'
})
export class ReceiptComponent implements OnInit {
  readonly icons = { back: ArrowLeft, ok: Check };

  private readonly billing = inject(BillingService);
  private readonly route = inject(ActivatedRoute);

  readonly receipt = signal<Receipt | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const orderId = Number(this.route.snapshot.paramMap.get('id'));
    try {
      this.receipt.set(await this.billing.getReceipt(orderId));
    } catch (e) {
      // A 409 here means the order has not been paid, so there is nothing to print yet.
      this.error.set(this.message(e));
    } finally {
      this.loading.set(false);
    }
  }

  /** Paper is still how a guest check leaves the building. The print stylesheet does the rest. */
  print(): void {
    window.print();
  }

  private message(e: unknown): string {
    return (
      (e as { error?: { detail?: string } })?.error?.detail ??
      'That receipt could not be loaded.'
    );
  }
}

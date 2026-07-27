import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ArrowLeft, Check } from '../../core/icons';
import { KitchenService } from './kitchen.service';
import { KitchenTicket } from './kitchen.models';

/**
 * The kitchen display (FR-11, FR-12): the live queue of tickets to cook, with one action per ticket.
 *
 * <p>Built as a pass display rather than a data table — it is read at arm's length across a hot
 * kitchen, so the table label, the ticket's age and the guest's notes are the things given size and
 * colour. Each ticket carries exactly one primary button whose label follows its status ("Start
 * cooking" while CONFIRMED, "Mark ready" while PREPARING), which puts every kitchen action one tap
 * from arrival (NFR-01).
 *
 * <p>Freshness is short polling (NFR-02) — see {@link KitchenComponent.POLL_MS}. The polling is
 * deliberately defensive: it never overlaps itself, never overwrites a just-mutated ticket with a
 * stale read, pauses while the tab is hidden, and on a failed poll keeps the last known queue
 * instead of blanking the screen the kitchen is working from.
 */
@Component({
  selector: 'app-kitchen',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './kitchen.component.html',
  styleUrl: './kitchen.component.css'
})
export class KitchenComponent implements OnInit {
  readonly icons = { back: ArrowLeft, ok: Check };

  /**
   * Poll interval. NFR-02 allows 3 s end to end and the worst case is `interval + request time`,
   * so the interval has to leave room for a full request. Measured `GET /kitchen/queue` against
   * the hosted (Neon) database: ~410 ms average, 453 ms worst of 12 samples — at a 2500 ms
   * interval the worst case is ~2950 ms, i.e. inside the budget by ~50 ms, which one slow query
   * erases. 2000 ms puts the worst case near 2450 ms and leaves real headroom.
   *
   * <p>Do not raise this without re-measuring the request time it is paired with.
   */
  private static readonly POLL_MS = 2000;

  /** Ticket age thresholds for the badge colour, in seconds. */
  private static readonly WARM_AFTER_S = 5 * 60;
  private static readonly LATE_AFTER_S = 10 * 60;

  private readonly kitchen = inject(KitchenService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tickets = signal<KitchenTicket[]>([]);
  /** True only until the first response — a refresh must never show a spinner (see `poll`). */
  readonly loading = signal(true);
  /** Set when polling is failing, so the screen can say "reconnecting" without a red banner. */
  readonly stale = signal(false);
  /** A short, non-alarming note (e.g. another cook got there first). */
  readonly notice = signal<string | null>(null);
  /** Id of the ticket whose action is in flight, so only its button shows a busy state. */
  readonly busyTicketId = signal<number | null>(null);

  /** Ticks once a second purely to drive the age badge and the "updated Ns ago" line. */
  private readonly now = signal(Date.now());
  /** When the currently displayed queue was fetched — the anchor for the ticking ages. */
  private readonly fetchedAt = signal(Date.now());

  /** A poll is in flight; the next tick must not stack a second request on top of it. */
  private inFlight = false;
  /** A mutation is in flight; polls are suspended so a stale read cannot undo it. */
  private mutating = false;

  readonly ticketCount = computed(() => this.tickets().length);
  readonly cooking = computed(
    () => this.tickets().filter((t) => t.status === 'PREPARING').length
  );
  readonly secondsSinceUpdate = computed(
    () => Math.max(0, Math.round((this.now() - this.fetchedAt()) / 1000))
  );

  ngOnInit(): void {
    void this.poll();

    const queueTimer = setInterval(() => {
      // A hidden tab has nobody reading it; the visibility listener refreshes on return.
      if (document.visibilityState === 'visible') {
        void this.poll();
      }
    }, KitchenComponent.POLL_MS);

    const clockTimer = setInterval(() => this.now.set(Date.now()), 1000);

    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        void this.poll();
      }
    };
    document.addEventListener('visibilitychange', onVisible);

    // Without this the interval outlives the component and polls forever after navigation.
    this.destroyRef.onDestroy(() => {
      clearInterval(queueTimer);
      clearInterval(clockTimer);
      document.removeEventListener('visibilitychange', onVisible);
    });
  }

  /**
   * Refreshes the queue. Skipped while another poll or a mutation is outstanding; a failure leaves
   * the last known tickets on screen and only flips {@link stale}, because a queue that blanks or
   * throws up a banner every couple of seconds is unusable in service.
   */
  async poll(): Promise<void> {
    if (this.inFlight || this.mutating) {
      return;
    }
    this.inFlight = true;
    try {
      this.replaceQueue(await this.kitchen.getQueue());
      this.stale.set(false);
    } catch {
      this.stale.set(true);
    } finally {
      this.inFlight = false;
      this.loading.set(false);
    }
  }

  /** Kitchen starts cooking this ticket (FR-12). */
  start(ticket: KitchenTicket): Promise<void> {
    return this.mutate(ticket, () => this.kitchen.prepare(ticket.id));
  }

  /** Kitchen is done; the ticket leaves the queue (FR-12). */
  finish(ticket: KitchenTicket): Promise<void> {
    return this.mutate(ticket, () => this.kitchen.ready(ticket.id));
  }

  /**
   * Runs a transition with polling suspended, then re-reads the queue from the server rather than
   * patching the list locally — the server is the authority on what is still queued, and a READY
   * ticket has to disappear entirely.
   *
   * <p>A 409 means the State machine refused it, which in a real kitchen almost always means
   * another cook tapped the same ticket first. That is not an error worth alarming anyone about, so
   * it is reported as a plain notice and the queue is refreshed to show the true status.
   */
  private async mutate(ticket: KitchenTicket, action: () => Promise<unknown>): Promise<void> {
    this.mutating = true;
    this.busyTicketId.set(ticket.id);
    this.notice.set(null);
    try {
      await action();
    } catch (e) {
      const status = (e as { status?: number })?.status;
      this.notice.set(
        status === 409
          ? `Table ${ticket.tableLabel} was already moved on by someone else.`
          : this.message(e)
      );
    } finally {
      this.mutating = false;
      this.busyTicketId.set(null);
      await this.poll();
    }
  }

  /** The label of a ticket's single action, which is also what its status means to a cook. */
  actionLabel(ticket: KitchenTicket): string {
    return ticket.status === 'CONFIRMED' ? 'Start cooking' : 'Mark ready';
  }

  /**
   * The ticket's age in seconds, ticking between polls: the server's `waitingSeconds` plus the time
   * elapsed locally since that value was fetched. Anchoring to a server value and adding a local
   * *elapsed* time keeps a skewed device clock from mis-ageing the ticket, which comparing against
   * `confirmedAt` directly would not.
   */
  age(ticket: KitchenTicket): number {
    return ticket.waitingSeconds + Math.max(0, Math.round((this.now() - this.fetchedAt()) / 1000));
  }

  /** mm:ss, so a glance reads the age without parsing a sentence. */
  ageLabel(ticket: KitchenTicket): string {
    const total = this.age(ticket);
    const minutes = Math.floor(total / 60);
    const seconds = total % 60;
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
  }

  /** Age band driving the badge colour, so the oldest ticket draws the eye unread. */
  ageBand(ticket: KitchenTicket): 'calm' | 'warm' | 'late' {
    const total = this.age(ticket);
    if (total >= KitchenComponent.LATE_AFTER_S) {
      return 'late';
    }
    return total >= KitchenComponent.WARM_AFTER_S ? 'warm' : 'calm';
  }

  private replaceQueue(tickets: KitchenTicket[]): void {
    this.tickets.set(tickets);
    this.fetchedAt.set(Date.now());
    this.now.set(Date.now());
  }

  private message(e: unknown): string {
    return (e as { error?: { detail?: string } })?.error?.detail
      ?? 'Something went wrong. Please try again.';
  }
}

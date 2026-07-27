import { OrderStatus } from '../orders/orders.models';

/**
 * One line to cook. Deliberately no price — the kitchen projection carries no money, so there is
 * nothing here for a pass display to show by accident.
 */
export interface KitchenTicketLine {
  menuItemName: string;
  quantity: number;
  notes: string | null;
}

/**
 * A ticket on the live queue (FR-11). `status` is always CONFIRMED or PREPARING — READY tickets
 * leave the queue (FR-12).
 *
 * `waitingSeconds` is the server's measure of the ticket's age at the moment of the read, not
 * something derived from `confirmedAt` on this device: a tablet with a skewed clock would otherwise
 * mis-age every ticket, and age is what the kitchen triages on.
 */
export interface KitchenTicket {
  id: number;
  tableLabel: string;
  status: OrderStatus;
  confirmedAt: string;
  waitingSeconds: number;
  items: KitchenTicketLine[];
}

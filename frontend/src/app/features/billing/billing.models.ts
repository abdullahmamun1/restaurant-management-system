import { OrderItem } from '../orders/orders.models';

/** How the money arrived (FR-15). Recorded, not processed — there is no gateway in this system. */
export type PaymentMethod = 'CASH' | 'CARD' | 'MOBILE';

/** One row of the cashier's worklist: a served table waiting to settle (FR-13). */
export interface PayableOrder {
  orderId: number;
  tableId: number;
  tableLabel: string;
  servedAt: string;
  itemCount: number;
  /** The full bill total including charges — never the bare subtotal. */
  grandTotal: number;
}

/** One named charge on a bill — "Tax", "Service charge" (FR-14). */
export interface BillLine {
  label: string;
  amount: number;
}

/**
 * The bill for a SERVED order (FR-13, FR-14). Every figure is computed server-side; nothing here
 * is multiplied, summed or rounded in the browser, so the screen and the receipt cannot disagree.
 *
 * `charges` is a labelled list rather than fixed tax/service fields on purpose — the client prints
 * whatever charges the server applied, in the server's order, without knowing what they are.
 */
export interface Bill {
  orderId: number;
  tableId: number;
  tableLabel: string;
  servedAt: string;
  items: OrderItem[];
  subtotal: number;
  charges: BillLine[];
  grandTotal: number;
}

/**
 * The receipt for a PAID order (FR-16). Its amounts are the snapshot frozen at payment, not a
 * recomputation — which is why tax and service charge are named fields here while a bill's charges
 * are an open list.
 */
export interface Receipt {
  orderId: number;
  tableId: number;
  tableLabel: string;
  paidAt: string;
  method: PaymentMethod;
  cashierUsername: string;
  items: OrderItem[];
  subtotal: number;
  taxAmount: number;
  serviceCharge: number;
  grandTotal: number;
}

/** The cashier confirms the method; the amount is the server's, never the client's. */
export interface RecordPaymentRequest {
  method: PaymentMethod;
}

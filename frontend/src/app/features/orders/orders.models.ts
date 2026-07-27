export type TableStatus = 'AVAILABLE' | 'OCCUPIED' | 'NEEDS_SERVICE';

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PREPARING'
  | 'READY'
  | 'SERVED'
  | 'PAID';

export interface RestaurantTable {
  id: number;
  label: string;
  seats: number;
  status: TableStatus;
  /** The open (unpaid) order on this table, or null when it is free. */
  activeOrderId: number | null;
  /**
   * That order's place in the lifecycle — null exactly when `activeOrderId` is.
   *
   * Not a restatement of `status`: a table reads OCCUPIED for the whole of an order's life, while
   * the order beneath it moves PENDING → … → SERVED. This is the one that tells a waiter what to
   * do next.
   */
  activeOrderStatus: OrderStatus | null;
}

export interface OrderItem {
  id: number;
  menuItemId: number;
  menuItemName: string;
  quantity: number;
  /** Price snapshotted when the line was added — not the item's current menu price. */
  unitPrice: number;
  lineTotal: number;
  notes: string | null;
}

export interface Order {
  id: number;
  tableId: number;
  tableLabel: string;
  status: OrderStatus;
  waiterUsername: string;
  createdAt: string;
  confirmedAt: string | null;
  servedAt: string | null;
  items: OrderItem[];
  subtotal: number;
  /** The server's answer to "may items still be changed?" (FR-07/FR-09) — never re-derived here. */
  editable: boolean;
}

export interface AddOrderItemRequest {
  menuItemId: number;
  quantity: number;
  notes: string | null;
}

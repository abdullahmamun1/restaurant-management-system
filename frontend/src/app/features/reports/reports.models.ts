/** Wire types for the Manager reporting API (FR-23, FR-24). */

/** One category's line in the FR-23 breakdown. */
export interface CategorySales {
  categoryId: number;
  categoryName: string;
  /**
   * Line revenue only — no share of tax or service charge, because those are order-level and no
   * category owns a portion of them. These sum to {@link SalesSummary.itemRevenue}, not to
   * `totalRevenue` (M7 D3). The dashboard says so on screen rather than leaving a manager to
   * discover it by adding up the bars.
   */
  revenue: number;
  quantitySold: number;
  /** Percentage of `itemRevenue`, computed server-side. The bar widths read this directly. */
  shareOfItemRevenue: number;
}

/**
 * FR-23's sales summary. Every amount is the frozen `payment` snapshot, never recomputed from the
 * current tax/service rates — so a report of a past range does not move when the rates change.
 *
 * The identity `itemRevenue + taxTotal + serviceChargeTotal === totalRevenue` holds exactly.
 */
export interface SalesSummary {
  from: string;
  to: string;
  /** The timezone the server resolved the dates in — echoed back so the report is self-describing. */
  zone: string;
  totalRevenue: number;
  ordersCompleted: number;
  itemRevenue: number;
  taxTotal: number;
  serviceChargeTotal: number;
  categoryBreakdown: CategorySales[];
}

/** One row of FR-24's top-sellers, ranked by `quantitySold`. */
export interface TopItem {
  menuItemId: number;
  name: string;
  categoryName: string;
  quantitySold: number;
  revenue: number;
}

/** The presets that make a common question one tap rather than two date pickers. */
export type RangePreset = 'today' | 'week' | 'month' | 'custom';

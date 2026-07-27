/**
 * The app's icon set, in one place.
 *
 * Every icon is a Lucide glyph — a stroked SVG that inherits `currentColor` and scales with the
 * element, which is why these replaced the emoji this UI used to carry: emoji are rendered by the
 * OS, so they arrived at a different weight, colour and size on every machine, and several
 * (the padlock, the receipt) were barely legible against a dark background.
 *
 * Collected here rather than imported ad hoc so the vocabulary stays consistent: one glyph means
 * one thing across every screen, and adding a screen means picking from this list rather than
 * inventing a new metaphor for "delete".
 */
export {
  // Navigation & session
  ArrowLeft,
  LogOut,

  // Stations (the modules on the home screen)
  UtensilsCrossed, // Menu management
  BookOpen,        // Menu browse
  Package,         // Inventory
  Armchair,        // Tables & orders
  Flame,           // Kitchen queue
  ReceiptText,     // Billing & payment
  ChartColumn,     // Reports & alerts
  Users,           // User management

  // Row & line actions
  Plus,
  Minus,
  Trash2,
  Pencil,
  History,
  SlidersHorizontal, // Adjust stock
  RefreshCw,
  Printer,

  // User management
  UserPlus,
  KeyRound,
  CircleCheck,
  CircleX,

  // Menu management & service
  FlaskConical, // Recipe
  Flag,         // Needs service
  Ban,          // Refused / blocked

  // Status & feedback
  Check,
  X,
  Lock,
  TriangleAlert,
  CircleAlert
} from 'lucide-angular';

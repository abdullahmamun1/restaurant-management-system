import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';

/**
 * Route table. `/login` is public; everything else sits behind {@link authGuard}, with
 * role-scoped feature routes additionally guarded by `roleGuard(...)`.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent)
  },
  {
    path: 'menu',
    canActivate: [roleGuard('MANAGER')],
    loadComponent: () =>
      import('./features/menu/menu-manage.component').then((m) => m.MenuManageComponent)
  },
  {
    path: 'menu/browse',
    canActivate: [roleGuard('MANAGER', 'WAITER')],
    loadComponent: () =>
      import('./features/menu/menu-browse.component').then((m) => m.MenuBrowseComponent)
  },
  {
    path: 'inventory',
    canActivate: [roleGuard('MANAGER')],
    loadComponent: () =>
      import('./features/inventory/inventory.component').then((m) => m.InventoryComponent)
  },
  // Table & Orders: the Waiter has full access, the other three roles read-only (SRS §2.1).
  // The components hide their write controls accordingly; the API enforces it (NFR-03).
  {
    path: 'orders',
    canActivate: [roleGuard('WAITER', 'MANAGER', 'KITCHEN', 'CASHIER')],
    loadComponent: () =>
      import('./features/orders/tables.component').then((m) => m.TablesComponent)
  },
  {
    path: 'orders/:id',
    canActivate: [roleGuard('WAITER', 'MANAGER', 'KITCHEN', 'CASHIER')],
    loadComponent: () =>
      import('./features/orders/order-detail.component').then((m) => m.OrderDetailComponent)
  },
  // Kitchen Queue is its own module in SRS §2.1 and Kitchen is the only role with any access to
  // it — stricter than Table & Orders above, where the other roles at least read.
  {
    path: 'kitchen',
    canActivate: [roleGuard('KITCHEN')],
    loadComponent: () =>
      import('./features/kitchen/kitchen.component').then((m) => m.KitchenComponent)
  },
  // Billing & Payment: the Cashier has full access, the Manager reads (SRS §2.1). Both routes
  // admit both roles; the till hides its method buttons and confirm for a Manager, and the API
  // rejects the write regardless of what the client shows (NFR-03).
  {
    path: 'billing',
    canActivate: [roleGuard('CASHIER', 'MANAGER')],
    loadComponent: () =>
      import('./features/billing/billing.component').then((m) => m.BillingComponent)
  },
  {
    path: 'billing/receipt/:id',
    canActivate: [roleGuard('CASHIER', 'MANAGER')],
    loadComponent: () =>
      import('./features/billing/receipt.component').then((m) => m.ReceiptComponent)
  },
  // Reports & Alerts is Manager-only in SRS §2.1 — no other role has any access, so this is the
  // strictest guard in the table. The API enforces it independently (NFR-03).
  {
    path: 'reports',
    canActivate: [roleGuard('MANAGER')],
    loadComponent: () =>
      import('./features/reports/reports.component').then((m) => m.ReportsComponent)
  },
  // User Management is Manager-only too (SRS §2.1).
  {
    path: 'users',
    canActivate: [roleGuard('MANAGER')],
    loadComponent: () =>
      import('./features/users/users.component').then((m) => m.UsersComponent)
  },
  { path: '**', redirectTo: '' }
];

import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';
import { AuthService } from '../../core/auth.service';
import { Role } from '../../core/models';
import {
  Armchair,
  ArrowLeft,
  BookOpen,
  ChartColumn,
  Flame,
  LogOut,
  Package,
  ReceiptText,
  UtensilsCrossed,
  Users
} from '../../core/icons';

interface Station {
  title: string;
  desc: string;
  icon: LucideIconData;
  /** When set, the station is a live link; otherwise it renders as "Opening soon". */
  route?: string;
}

/** The modules each role owns (SRS §2.1 access matrix) — shown as their "stations". */
const STATIONS: Record<Role, Station[]> = {
  MANAGER: [
    { icon: UtensilsCrossed, title: 'Menu Management', desc: 'Categories, items & availability', route: '/menu' },
    { icon: Package, title: 'Inventory', desc: 'Stock, thresholds & adjustments', route: '/inventory' },
    { icon: ReceiptText, title: 'Billing & Payment', desc: 'Review settled checks & receipts', route: '/billing' },
    { icon: ChartColumn, title: 'Reports & Alerts', desc: 'Sales summaries & low-stock', route: '/reports' },
    { icon: Users, title: 'User Management', desc: 'Staff accounts & roles', route: '/users' }
  ],
  WAITER: [
    { icon: Armchair, title: 'Tables & Orders', desc: 'Open tables, build & confirm orders', route: '/orders' },
    { icon: BookOpen, title: 'Menu', desc: 'Browse the current menu', route: '/menu/browse' }
  ],
  KITCHEN: [
    { icon: Flame, title: 'Kitchen Queue', desc: 'Live tickets — prepare & mark ready', route: '/kitchen' }
  ],
  CASHIER: [
    { icon: ReceiptText, title: 'Billing & Payment', desc: 'Settle checks & print receipts', route: '/billing' }
  ]
};

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  private readonly auth = inject(AuthService);

  readonly icons = { arrowLeft: ArrowLeft, logOut: LogOut };

  readonly user = this.auth.user;
  readonly stations = computed<Station[]>(() => {
    const role = this.user()?.role;
    return role ? STATIONS[role] : [];
  });

  logout(): void {
    this.auth.logout();
  }
}

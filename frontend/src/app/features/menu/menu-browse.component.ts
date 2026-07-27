import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ArrowLeft } from '../../core/icons';
import { MenuService } from './menu.service';
import { MenuCategory, MenuItem } from './menu.models';

interface CategoryGroup {
  category: MenuCategory;
  items: MenuItem[];
}

/**
 * Read-only menu browse for Waiters (and Managers) — SRS §2.1 Menu = Read for Waiter.
 * Unavailable items are shown dimmed / "86'd" to preview the FR-05 rule enforced at ordering.
 */
@Component({
  selector: 'app-menu-browse',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './menu-browse.component.html',
  styleUrl: './menu-browse.component.css'
})
export class MenuBrowseComponent implements OnInit {
  readonly icons = { back: ArrowLeft };

  private readonly menu = inject(MenuService);

  readonly loading = signal(true);
  readonly categories = signal<MenuCategory[]>([]);
  readonly items = signal<MenuItem[]>([]);

  readonly groups = computed<CategoryGroup[]>(() => {
    const items = this.items();
    return this.categories()
      .map((category) => ({
        category,
        items: items
          .filter((i) => i.categoryId === category.id)
          .sort((a, b) => a.name.localeCompare(b.name))
      }))
      .filter((g) => g.items.length > 0);
  });

  async ngOnInit(): Promise<void> {
    try {
      const [cats, items] = await Promise.all([
        this.menu.listCategories(),
        this.menu.listItems()
      ]);
      this.categories.set(cats);
      this.items.set(items);
    } finally {
      this.loading.set(false);
    }
  }
}

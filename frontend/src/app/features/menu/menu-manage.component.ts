import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ArrowLeft, CircleAlert, FlaskConical, Pencil, Trash2, X } from '../../core/icons';
import { MenuService } from './menu.service';
import { MenuCategory, MenuItem } from './menu.models';
import { InventoryService } from '../inventory/inventory.service';
import { Ingredient } from '../inventory/inventory.models';

type ItemDialog = { mode: 'create' | 'edit'; item?: MenuItem };
type CategoryDialog = { mode: 'create' | 'edit'; category?: MenuCategory };
type Confirm = { message: string; run: () => Promise<void> };
type RecipeLineDraft = { ingredientId: number | null; quantity: number | null };

/**
 * Manager-only Menu Management screen (FR-03, FR-04). Holds all view state in signals and
 * delegates persistence to {@link MenuService}; after each mutation it reloads to stay in sync.
 */
@Component({
  selector: 'app-menu-manage',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule],
  templateUrl: './menu-manage.component.html',
  styleUrl: './menu-manage.component.css'
})
export class MenuManageComponent implements OnInit {
  readonly icons = { back: ArrowLeft, alert: CircleAlert, recipe: FlaskConical, edit: Pencil, delete: Trash2, close: X };

  private readonly menu = inject(MenuService);
  private readonly inventory = inject(InventoryService);
  private readonly fb = inject(FormBuilder);

  readonly categories = signal<MenuCategory[]>([]);
  readonly items = signal<MenuItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly selectedCategoryId = signal<number | null>(null);

  readonly itemDialog = signal<ItemDialog | null>(null);
  readonly categoryDialog = signal<CategoryDialog | null>(null);
  readonly confirm = signal<Confirm | null>(null);
  readonly saving = signal(false);

  // Recipe editor state
  readonly recipeItem = signal<MenuItem | null>(null);
  readonly recipeLines = signal<RecipeLineDraft[]>([]);
  readonly ingredients = signal<Ingredient[]>([]);

  readonly visibleItems = computed(() => {
    const cid = this.selectedCategoryId();
    const items = this.items();
    return cid == null ? items : items.filter((i) => i.categoryId === cid);
  });

  readonly itemForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    description: [''],
    price: [0, [Validators.required, Validators.min(0)]],
    categoryId: [0, Validators.required],
    available: [true]
  });

  readonly categoryForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    sortOrder: [0, [Validators.required, Validators.min(0)]]
  });

  ngOnInit(): void {
    this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    try {
      const [cats, items, ings] = await Promise.all([
        this.menu.listCategories(),
        this.menu.listItems(),
        this.inventory.listIngredients()
      ]);
      this.categories.set(cats);
      this.items.set(items);
      this.ingredients.set(ings);
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.loading.set(false);
    }
  }

  selectCategory(id: number | null): void {
    this.selectedCategoryId.set(id);
  }

  categoryName(id: number): string {
    return this.categories().find((c) => c.id === id)?.name ?? '';
  }

  // ---- Category dialog ---------------------------------------------------
  openCreateCategory(): void {
    this.categoryForm.reset({ name: '', sortOrder: this.categories().length });
    this.categoryDialog.set({ mode: 'create' });
  }

  openEditCategory(category: MenuCategory): void {
    this.categoryForm.reset({ name: category.name, sortOrder: category.sortOrder });
    this.categoryDialog.set({ mode: 'edit', category });
  }

  async saveCategory(): Promise<void> {
    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      return;
    }
    const dialog = this.categoryDialog();
    if (!dialog) return;
    await this.run(async () => {
      const body = this.categoryForm.getRawValue();
      if (dialog.mode === 'create') {
        await this.menu.createCategory(body);
      } else if (dialog.category) {
        await this.menu.updateCategory(dialog.category.id, body);
      }
      this.categoryDialog.set(null);
      await this.reload();
    });
  }

  askDeleteCategory(category: MenuCategory): void {
    this.confirm.set({
      message: `Delete category "${category.name}"?`,
      run: async () => {
        await this.menu.deleteCategory(category.id);
        if (this.selectedCategoryId() === category.id) this.selectedCategoryId.set(null);
        await this.reload();
      }
    });
  }

  // ---- Item dialog -------------------------------------------------------
  openCreateItem(): void {
    const defaultCat = this.selectedCategoryId() ?? this.categories()[0]?.id ?? 0;
    this.itemForm.reset({
      name: '',
      description: '',
      price: 0,
      categoryId: defaultCat,
      available: true
    });
    this.itemDialog.set({ mode: 'create' });
  }

  openEditItem(item: MenuItem): void {
    this.itemForm.reset({
      name: item.name,
      description: item.description ?? '',
      price: item.price,
      categoryId: item.categoryId,
      available: item.available
    });
    this.itemDialog.set({ mode: 'edit', item });
  }

  async saveItem(): Promise<void> {
    if (this.itemForm.invalid) {
      this.itemForm.markAllAsTouched();
      return;
    }
    const dialog = this.itemDialog();
    if (!dialog) return;
    await this.run(async () => {
      const raw = this.itemForm.getRawValue();
      const body = {
        name: raw.name,
        description: raw.description?.trim() ? raw.description.trim() : null,
        price: Number(raw.price),
        categoryId: Number(raw.categoryId),
        available: raw.available
      };
      if (dialog.mode === 'create') {
        await this.menu.createItem(body);
      } else if (dialog.item) {
        await this.menu.updateItem(dialog.item.id, body);
      }
      this.itemDialog.set(null);
      await this.reload();
    });
  }

  async toggleAvailability(item: MenuItem): Promise<void> {
    await this.run(async () => {
      const updated = await this.menu.setAvailability(item.id, !item.available);
      this.items.update((list) => list.map((i) => (i.id === updated.id ? updated : i)));
    });
  }

  askDeleteItem(item: MenuItem): void {
    this.confirm.set({
      message: `Delete "${item.name}"?`,
      run: async () => {
        await this.menu.deleteItem(item.id);
        await this.reload();
      }
    });
  }

  async runConfirm(): Promise<void> {
    const c = this.confirm();
    if (!c) return;
    await this.run(async () => {
      await c.run();
      this.confirm.set(null);
    });
  }

  // ---- Recipe editor -----------------------------------------------------
  async openRecipe(item: MenuItem): Promise<void> {
    this.recipeItem.set(item);
    this.recipeLines.set([]);
    await this.run(async () => {
      const recipe = await this.menu.getRecipe(item.id);
      this.recipeLines.set(
        recipe.lines.map((l) => ({ ingredientId: l.ingredientId, quantity: l.quantity }))
      );
    });
  }

  addRecipeLine(): void {
    const firstUnused = this.ingredients().find(
      (ing) => !this.recipeLines().some((l) => l.ingredientId === ing.id)
    );
    this.recipeLines.update((lines) => [
      ...lines,
      { ingredientId: firstUnused?.id ?? this.ingredients()[0]?.id ?? null, quantity: 1 }
    ]);
  }

  removeRecipeLine(index: number): void {
    this.recipeLines.update((lines) => lines.filter((_, i) => i !== index));
  }

  setLineIngredient(index: number, ingredientId: number): void {
    this.recipeLines.update((lines) =>
      lines.map((l, i) => (i === index ? { ...l, ingredientId } : l))
    );
  }

  setLineQuantity(index: number, quantity: number): void {
    this.recipeLines.update((lines) =>
      lines.map((l, i) => (i === index ? { ...l, quantity } : l))
    );
  }

  unitFor(ingredientId: number | null): string {
    return this.ingredients().find((i) => i.id === ingredientId)?.unit ?? '';
  }

  async saveRecipe(): Promise<void> {
    const item = this.recipeItem();
    if (!item) return;
    const lines = this.recipeLines()
      .filter((l) => l.ingredientId != null && l.quantity != null && l.quantity > 0)
      .map((l) => ({ ingredientId: l.ingredientId as number, quantity: l.quantity as number }));
    await this.run(async () => {
      await this.menu.saveRecipe(item.id, { lines });
      this.recipeItem.set(null);
    });
  }

  closeDialogs(): void {
    this.itemDialog.set(null);
    this.categoryDialog.set(null);
    this.confirm.set(null);
    this.recipeItem.set(null);
  }

  /** Runs an async mutation, surfacing any server message (e.g. a 409) as an error banner. */
  private async run(fn: () => Promise<void>): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await fn();
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.saving.set(false);
    }
  }

  private message(e: unknown): string {
    const detail = (e as { error?: { detail?: string } })?.error?.detail;
    return detail ?? 'Something went wrong. Please try again.';
  }
}

import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import {
  ArrowLeft,
  CircleAlert,
  History,
  Pencil,
  Plus,
  SlidersHorizontal,
  Trash2,
  TriangleAlert
} from '../../core/icons';
import { InventoryService } from './inventory.service';
import { Adjustment, Ingredient } from './inventory.models';

type IngredientDialog = { mode: 'create' | 'edit'; ingredient?: Ingredient };
type Confirm = { message: string; run: () => Promise<void> };

/**
 * Manager-only Inventory screen (FR-17, FR-18, FR-21). State in signals; persistence via
 * {@link InventoryService}. Stock is changed only through the Adjust dialog, which records an
 * audit entry; the History dialog shows that immutable log.
 */
@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, LucideAngularModule],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.css'
})
export class InventoryComponent implements OnInit {
  private readonly inventory = inject(InventoryService);
  private readonly fb = inject(FormBuilder);

  readonly icons = {
    back: ArrowLeft,
    add: Plus,
    adjust: SlidersHorizontal,
    history: History,
    edit: Pencil,
    delete: Trash2,
    warn: TriangleAlert,
    alert: CircleAlert
  };

  readonly ingredients = signal<Ingredient[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly lowStockOnly = signal(false);

  readonly ingredientDialog = signal<IngredientDialog | null>(null);
  readonly adjustTarget = signal<Ingredient | null>(null);
  readonly historyFor = signal<Ingredient | null>(null);
  readonly historyRows = signal<Adjustment[]>([]);
  readonly confirm = signal<Confirm | null>(null);

  readonly lowStockCount = computed(() => this.ingredients().filter((i) => i.lowStock).length);
  readonly visible = computed(() =>
    this.lowStockOnly() ? this.ingredients().filter((i) => i.lowStock) : this.ingredients()
  );

  readonly ingredientForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    unit: ['', Validators.required],
    initialStock: [0, [Validators.required, Validators.min(0)]],
    lowStockThreshold: [0, [Validators.required, Validators.min(0)]]
  });

  readonly adjustForm = this.fb.nonNullable.group({
    quantityChange: [0, Validators.required],
    reason: ['', Validators.required]
  });

  ngOnInit(): void {
    this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    try {
      this.ingredients.set(await this.inventory.listIngredients());
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.loading.set(false);
    }
  }

  // ---- Ingredient create/edit -------------------------------------------
  openCreate(): void {
    this.ingredientForm.reset({ name: '', unit: '', initialStock: 0, lowStockThreshold: 0 });
    this.ingredientDialog.set({ mode: 'create' });
  }

  openEdit(ingredient: Ingredient): void {
    this.ingredientForm.reset({
      name: ingredient.name,
      unit: ingredient.unit,
      initialStock: ingredient.stockQty,
      lowStockThreshold: ingredient.lowStockThreshold
    });
    this.ingredientDialog.set({ mode: 'edit', ingredient });
  }

  async saveIngredient(): Promise<void> {
    const dialog = this.ingredientDialog();
    if (!dialog) return;
    const controls = dialog.mode === 'create'
      ? this.ingredientForm.valid
      : this.ingredientForm.controls.name.valid && this.ingredientForm.controls.unit.valid
        && this.ingredientForm.controls.lowStockThreshold.valid;
    if (!controls) {
      this.ingredientForm.markAllAsTouched();
      return;
    }
    await this.run(async () => {
      const v = this.ingredientForm.getRawValue();
      if (dialog.mode === 'create') {
        await this.inventory.create({
          name: v.name,
          unit: v.unit,
          initialStock: Number(v.initialStock),
          lowStockThreshold: Number(v.lowStockThreshold)
        });
      } else if (dialog.ingredient) {
        await this.inventory.update(dialog.ingredient.id, {
          name: v.name,
          unit: v.unit,
          lowStockThreshold: Number(v.lowStockThreshold)
        });
      }
      this.ingredientDialog.set(null);
      await this.reload();
    });
  }

  askDelete(ingredient: Ingredient): void {
    this.confirm.set({
      message: `Delete ingredient "${ingredient.name}"?`,
      run: async () => {
        await this.inventory.remove(ingredient.id);
        await this.reload();
      }
    });
  }

  // ---- Adjust stock ------------------------------------------------------
  openAdjust(ingredient: Ingredient): void {
    this.adjustForm.reset({ quantityChange: 0, reason: '' });
    this.adjustTarget.set(ingredient);
  }

  async saveAdjust(): Promise<void> {
    const target = this.adjustTarget();
    if (!target) return;
    if (this.adjustForm.invalid) {
      this.adjustForm.markAllAsTouched();
      return;
    }
    await this.run(async () => {
      const v = this.adjustForm.getRawValue();
      await this.inventory.adjust(target.id, {
        quantityChange: Number(v.quantityChange),
        reason: v.reason
      });
      this.adjustTarget.set(null);
      await this.reload();
    });
  }

  // ---- History -----------------------------------------------------------
  async openHistory(ingredient: Ingredient): Promise<void> {
    this.historyFor.set(ingredient);
    this.historyRows.set([]);
    try {
      this.historyRows.set(await this.inventory.history(ingredient.id));
    } catch (e) {
      this.error.set(this.message(e));
    }
  }

  async runConfirm(): Promise<void> {
    const c = this.confirm();
    if (!c) return;
    await this.run(async () => {
      await c.run();
      this.confirm.set(null);
    });
  }

  closeDialogs(): void {
    this.ingredientDialog.set(null);
    this.adjustTarget.set(null);
    this.historyFor.set(null);
    this.confirm.set(null);
  }

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
    return (e as { error?: { detail?: string } })?.error?.detail
      ?? 'Something went wrong. Please try again.';
  }
}

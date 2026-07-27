export interface Ingredient {
  id: number;
  name: string;
  unit: string;
  stockQty: number;
  lowStockThreshold: number;
  lowStock: boolean;
}

export interface Adjustment {
  id: number;
  quantityChange: number;
  reason: string;
  resultingStock: number;
  managerUsername: string;
  createdAt: string;
}

export interface IngredientCreateRequest {
  name: string;
  unit: string;
  initialStock: number;
  lowStockThreshold: number;
}

export interface IngredientUpdateRequest {
  name: string;
  unit: string;
  lowStockThreshold: number;
}

export interface AdjustmentRequest {
  quantityChange: number;
  reason: string;
}

export interface MenuCategory {
  id: number;
  name: string;
  sortOrder: number;
}

export interface MenuItem {
  id: number;
  name: string;
  description: string | null;
  price: number;
  categoryId: number;
  categoryName: string;
  available: boolean;
}

export interface CategoryRequest {
  name: string;
  sortOrder: number;
}

export interface MenuItemRequest {
  name: string;
  description: string | null;
  price: number;
  categoryId: number;
  available: boolean;
}

export interface RecipeLine {
  ingredientId: number;
  ingredientName: string;
  unit: string;
  quantity: number;
}

export interface Recipe {
  menuItemId: number;
  menuItemName: string;
  lines: RecipeLine[];
}

export interface RecipeLineInput {
  ingredientId: number;
  quantity: number;
}

export interface RecipeRequest {
  lines: RecipeLineInput[];
}

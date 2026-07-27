-- V4__inventory.sql  (M3: Inventory & Recipe Foundation)
-- Ingredient-level inventory (FR-17, FR-18), recipe mappings, and an immutable stock-adjustment
-- audit log (FR-21, NFR-06). Manager-only domain (SRS §2.1).

CREATE TABLE ingredient (
    id                  BIGSERIAL     PRIMARY KEY,
    name                VARCHAR(120)  NOT NULL UNIQUE,
    unit                VARCHAR(20)   NOT NULL,
    stock_qty           NUMERIC(12,3) NOT NULL DEFAULT 0,
    low_stock_threshold NUMERIC(12,3) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- FR-22: stock can never be negative (also guarded in the domain + service).
    CONSTRAINT ingredient_stock_nonneg CHECK (stock_qty >= 0),
    CONSTRAINT ingredient_threshold_nonneg CHECK (low_stock_threshold >= 0)
);

-- Recipe: how much of each ingredient a menu item consumes per unit.
CREATE TABLE recipe_item (
    id           BIGSERIAL     PRIMARY KEY,
    menu_item_id BIGINT        NOT NULL,
    ingredient_id BIGINT       NOT NULL,
    quantity     NUMERIC(12,3) NOT NULL,
    CONSTRAINT recipe_item_qty_pos CHECK (quantity > 0),
    -- Deleting a menu item removes its recipe rows; an ingredient in use cannot be deleted.
    CONSTRAINT fk_recipe_menu_item
        FOREIGN KEY (menu_item_id) REFERENCES menu_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredient (id) ON DELETE RESTRICT,
    CONSTRAINT uq_recipe_item UNIQUE (menu_item_id, ingredient_id)
);

CREATE INDEX idx_recipe_menu_item ON recipe_item (menu_item_id);
CREATE INDEX idx_recipe_ingredient ON recipe_item (ingredient_id);

-- Append-only audit log of every manual stock change (FR-21, NFR-06). No UPDATE/DELETE is
-- ever exposed through the application.
CREATE TABLE inventory_adjustment (
    id              BIGSERIAL     PRIMARY KEY,
    ingredient_id   BIGINT        NOT NULL,
    manager_id      BIGINT        NOT NULL,
    quantity_change NUMERIC(12,3) NOT NULL,
    reason          VARCHAR(300)  NOT NULL,
    resulting_stock NUMERIC(12,3) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_adjustment_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredient (id) ON DELETE RESTRICT,
    CONSTRAINT fk_adjustment_manager
        FOREIGN KEY (manager_id) REFERENCES app_user (id) ON DELETE RESTRICT
);

CREATE INDEX idx_adjustment_ingredient ON inventory_adjustment (ingredient_id, created_at DESC);

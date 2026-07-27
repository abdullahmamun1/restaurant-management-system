-- V3__menu.sql  (M2: Menu Management)
-- Menu categories and items (FR-03, FR-04). Managed by the Manager; readable by the Waiter.

CREATE TABLE menu_category (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL UNIQUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE menu_item (
    id          BIGSERIAL     PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(400),
    price       NUMERIC(10,2) NOT NULL,
    category_id BIGINT        NOT NULL,
    available   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT menu_item_price_nonneg CHECK (price >= 0),
    -- RESTRICT: a category cannot be deleted while it still has items (blocked, not cascaded).
    CONSTRAINT fk_menu_item_category
        FOREIGN KEY (category_id) REFERENCES menu_category (id) ON DELETE RESTRICT,
    -- Item names are unique within a category.
    CONSTRAINT uq_menu_item_category_name UNIQUE (category_id, name)
);

CREATE INDEX idx_menu_item_category ON menu_item (category_id);

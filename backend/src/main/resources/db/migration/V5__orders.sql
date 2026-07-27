-- V5__orders.sql  (M4: Table & Order Lifecycle)
-- Dine-in tables, orders, and order lines (FR-06–FR-10). Waiter-owned domain (SRS §2.1);
-- Manager/Kitchen/Cashier read. Status CHECK constraints mirror the OrderStatus/TableStatus
-- state machines in the domain layer.

CREATE TABLE restaurant_table (
    id         BIGSERIAL   PRIMARY KEY,
    label      VARCHAR(20) NOT NULL UNIQUE,
    seats      INT         NOT NULL DEFAULT 2,
    status     VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT restaurant_table_status_valid
        CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'NEEDS_SERVICE')),
    CONSTRAINT restaurant_table_seats_pos CHECK (seats > 0)
);

-- "order" is a reserved word in SQL — the table is "orders".
CREATE TABLE orders (
    id           BIGSERIAL   PRIMARY KEY,
    table_id     BIGINT      NOT NULL,
    waiter_id    BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ,
    served_at    TIMESTAMPTZ,
    CONSTRAINT orders_status_valid CHECK (status IN
        ('PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'SERVED', 'PAID')),
    CONSTRAINT fk_orders_table
        FOREIGN KEY (table_id)  REFERENCES restaurant_table (id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_waiter
        FOREIGN KEY (waiter_id) REFERENCES app_user (id)         ON DELETE RESTRICT
);

-- At most one open (unpaid) order per table (FR-06). DB-level partial unique index backing the
-- service-level guard, in the same belt-and-braces spirit as the stock CHECK (FR-22).
CREATE UNIQUE INDEX uq_active_order_per_table
    ON orders (table_id) WHERE status <> 'PAID';

-- Supports the M5 kitchen queue: CONFIRMED/PREPARING ordered by confirmed_at (FR-11).
CREATE INDEX idx_orders_status_confirmed ON orders (status, confirmed_at);

CREATE TABLE order_item (
    id           BIGSERIAL     PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    menu_item_id BIGINT        NOT NULL,
    quantity     INT           NOT NULL,
    -- Price snapshot at add time: a later menu price edit must not retroactively change an
    -- open or paid order's total (needed for M6 billing and M7 revenue reports to be truthful).
    unit_price   NUMERIC(10,2) NOT NULL,
    notes        VARCHAR(300),
    CONSTRAINT order_item_qty_pos CHECK (quantity > 0),
    CONSTRAINT order_item_price_nonneg CHECK (unit_price >= 0),
    -- Lines are owned by the order (CASCADE); a menu item that has been ordered cannot be
    -- deleted (RESTRICT) — MenuService blocks it with a descriptive 409 first.
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id)     REFERENCES orders (id)    ON DELETE CASCADE,
    CONSTRAINT fk_order_item_menu_item
        FOREIGN KEY (menu_item_id) REFERENCES menu_item (id) ON DELETE RESTRICT
);

CREATE INDEX idx_order_item_order ON order_item (order_id);

-- Seed the dining room floor.
INSERT INTO restaurant_table (label, seats) VALUES
    ('T1', 2), ('T2', 2), ('T3', 4), ('T4', 4),
    ('T5', 4), ('T6', 6), ('T7', 6), ('T8', 8);

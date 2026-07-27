-- V6__billing.sql  (M6: Billing, Payment & Atomic Inventory Deduction)
-- One payment per order (SRS §2.3), snapshotting the amounts charged. There is deliberately no
-- `bill` table: an unpaid bill is computed from the order (order_item.unit_price is already an
-- immutable snapshot), and a paid one is this row. What is *not* derivable later are the rates,
-- which live in config and may change — so they are frozen here too.

CREATE TABLE payment (
    id                   BIGSERIAL     PRIMARY KEY,
    -- UNIQUE is the DB half of "payment is recorded once per order"; the State machine
    -- (PAID is terminal) is the application half. Same belt-and-braces as FR-22's CHECK.
    order_id             BIGINT        NOT NULL UNIQUE,
    cashier_id           BIGINT        NOT NULL,
    method               VARCHAR(20)   NOT NULL,
    subtotal             NUMERIC(10,2) NOT NULL,
    tax_amount           NUMERIC(10,2) NOT NULL,
    service_charge       NUMERIC(10,2) NOT NULL,
    grand_total          NUMERIC(10,2) NOT NULL,
    -- The rates in force at the time. Not required by FR-16 (which needs the amounts), but they
    -- make the row self-explanatory when config has since changed, and M7 reports read it.
    tax_rate             NUMERIC(6,4)  NOT NULL,
    service_charge_rate  NUMERIC(6,4)  NOT NULL,
    paid_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT payment_method_valid CHECK (method IN ('CASH', 'CARD', 'MOBILE')),
    CONSTRAINT payment_amounts_nonneg CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND service_charge >= 0 AND grand_total >= 0),
    CONSTRAINT payment_rates_nonneg CHECK (tax_rate >= 0 AND service_charge_rate >= 0),
    -- The arithmetic itself is a constraint: a receipt whose parts do not sum to its total is
    -- never acceptable, so the DB refuses it too.
    CONSTRAINT payment_total_consistent CHECK (
        grand_total = subtotal + tax_amount + service_charge),
    -- No ON DELETE CASCADE anywhere here: a paid order must not become deletable by cascade.
    CONSTRAINT fk_payment_order   FOREIGN KEY (order_id)   REFERENCES orders (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_payment_cashier FOREIGN KEY (cashier_id) REFERENCES app_user (id) ON DELETE RESTRICT
);

-- For M7's date-ranged sales reports (FR-23), not for M6.
CREATE INDEX idx_payment_paid_at ON payment (paid_at DESC);

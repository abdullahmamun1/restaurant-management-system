-- V1__baseline.sql
-- Baseline migration for the Dine-In Restaurant Ordering and Inventory System.
--
-- This file intentionally contains no domain tables. It establishes the Flyway
-- migration baseline (M0). Subsequent milestones add their own versioned migrations:
--   V2  -> users & roles           (M1: authentication & RBAC)
--   V3  -> menu categories & items (M2: menu management)
--   V4  -> ingredients, recipes, inventory audit log (M3: inventory)
--   V5  -> tables, orders, order items                (M4: ordering)
--   V6  -> bills, payments                            (M6: billing)
--
-- Keeping V1 empty gives us a clean, ordered migration history from the very first commit.

-- Marker so the baseline is a valid, non-empty migration.
CREATE TABLE IF NOT EXISTS schema_bootstrap (
    id          SMALLINT     PRIMARY KEY DEFAULT 1,
    initialized BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT schema_bootstrap_singleton CHECK (id = 1)
);

INSERT INTO schema_bootstrap (id) VALUES (1)
    ON CONFLICT (id) DO NOTHING;

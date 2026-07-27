-- V8__audit_immutability.sql  (M8: NFR-06)
--
-- The FR-21 adjustment log is append-only.
--
-- The application already had no code path that mutates one, and M8 narrows the repository so that
-- `delete` is not even on the interface -- but "no caller does this" is a property of today's code,
-- not of the data. This makes it a property of the database, which is the same belt-and-braces
-- treatment FR-22 gets from `ingredient_stock_nonneg` and FR-06 from `uq_active_order_per_table`.
--
-- BEFORE, not AFTER: the row must never change, so the exception is raised before the write rather
-- than after it has happened and been rolled back.
--
-- Deliberately absolute. There is no "unless a manager really means it" escape hatch, because a log
-- with an escape hatch is not an audit log. If a row is ever genuinely wrong, the correction is a
-- compensating adjustment -- which is exactly what this log exists to record.

CREATE OR REPLACE FUNCTION inventory_adjustment_is_append_only()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'inventory_adjustment is append-only (FR-21, NFR-06): % is not permitted on row id %',
        TG_OP, OLD.id
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_adjustment_append_only
    BEFORE UPDATE OR DELETE ON inventory_adjustment
    FOR EACH ROW EXECUTE FUNCTION inventory_adjustment_is_append_only();

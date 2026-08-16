-- Nullable branch on trade vouchers. Null = shop-level / unassigned (included in consolidated TB).
-- Existing rows stay valid; shop_id isolation is unchanged. No backfill.

ALTER TABLE ledger_vouchers
    ADD COLUMN IF NOT EXISTS branch_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_ledger_vouchers_shop_branch
    ON ledger_vouchers (tenant_id, shop_id, branch_id);

ALTER TABLE expense_entry
    ADD COLUMN IF NOT EXISTS branch_id BIGINT;

ALTER TABLE other_income_entry
    ADD COLUMN IF NOT EXISTS branch_id BIGINT;

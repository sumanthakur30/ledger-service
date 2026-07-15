-- Idempotent trade invoice → voucher mapping
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_voucher_source
    ON ledger_vouchers (tenant_id, shop_id, source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;

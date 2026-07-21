-- Wave E: period lock + bank line reconciliation ticks.
CREATE TABLE IF NOT EXISTS shop_period_locks (
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    locked_through DATE NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(120),
    PRIMARY KEY (tenant_id, shop_id)
);

ALTER TABLE ledger_voucher_lines
    ADD COLUMN IF NOT EXISTS reconciled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ledger_voucher_lines
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMP;

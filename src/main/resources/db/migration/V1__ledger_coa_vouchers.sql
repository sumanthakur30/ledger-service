-- Chart of accounts + double-entry vouchers (trade GL; not clinic revenue_ledger).
CREATE TABLE IF NOT EXISTS ledger_accounts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    parent_id BIGINT REFERENCES ledger_accounts (id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    system_account BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ledger_account_shop_code UNIQUE (tenant_id, shop_id, code)
);

CREATE INDEX IF NOT EXISTS idx_ledger_accounts_shop
    ON ledger_accounts (tenant_id, shop_id, account_type);

CREATE TABLE IF NOT EXISTS ledger_vouchers (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    voucher_number VARCHAR(50) NOT NULL,
    voucher_date DATE NOT NULL,
    voucher_type VARCHAR(30) NOT NULL DEFAULT 'JOURNAL',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    narration VARCHAR(500),
    source_type VARCHAR(40),
    source_id BIGINT,
    total_debit DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_credit DOUBLE PRECISION NOT NULL DEFAULT 0,
    posted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ledger_voucher_number UNIQUE (tenant_id, shop_id, voucher_number)
);

CREATE INDEX IF NOT EXISTS idx_ledger_vouchers_shop_date
    ON ledger_vouchers (tenant_id, shop_id, voucher_date DESC);

CREATE INDEX IF NOT EXISTS idx_ledger_vouchers_source
    ON ledger_vouchers (tenant_id, shop_id, source_type, source_id);

CREATE TABLE IF NOT EXISTS ledger_voucher_lines (
    id BIGSERIAL PRIMARY KEY,
    voucher_id BIGINT NOT NULL REFERENCES ledger_vouchers (id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES ledger_accounts (id),
    debit DOUBLE PRECISION NOT NULL DEFAULT 0,
    credit DOUBLE PRECISION NOT NULL DEFAULT 0,
    line_narration VARCHAR(255),
    line_no INT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_ledger_voucher_lines_voucher
    ON ledger_voucher_lines (voucher_id);

CREATE INDEX IF NOT EXISTS idx_ledger_voucher_lines_account
    ON ledger_voucher_lines (account_id);

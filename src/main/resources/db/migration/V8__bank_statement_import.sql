-- Bank / cash statement CSV import on existing voucher-line recon ticks.
-- File import only — not a live bank API.

CREATE TABLE IF NOT EXISTS bank_statement_import_batches (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    account_code VARCHAR(20) NOT NULL,
    file_name VARCHAR(255),
    from_date DATE,
    to_date DATE,
    line_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_bank_stmt_batch_shop
    ON bank_statement_import_batches (tenant_id, shop_id, created_at DESC);

CREATE TABLE IF NOT EXISTS bank_statement_lines (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES bank_statement_import_batches (id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    line_no INT NOT NULL,
    txn_date DATE,
    value_date DATE,
    description VARCHAR(500),
    reference VARCHAR(80),
    debit DOUBLE PRECISION NOT NULL DEFAULT 0,
    credit DOUBLE PRECISION NOT NULL DEFAULT 0,
    balance DOUBLE PRECISION,
    match_status VARCHAR(30),
    matched_line_id BIGINT,
    CONSTRAINT uq_bank_stmt_line_no UNIQUE (batch_id, line_no)
);

CREATE INDEX IF NOT EXISTS idx_bank_stmt_lines_batch
    ON bank_statement_lines (batch_id, line_no);

CREATE INDEX IF NOT EXISTS idx_bank_stmt_lines_shop
    ON bank_statement_lines (tenant_id, shop_id, batch_id);

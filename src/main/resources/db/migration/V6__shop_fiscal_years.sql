-- Named financial years (Indian Apr–Mar by default). Period lock remains the posting cutoff.
-- Opening balances carry in the perpetual GL; year-end close posts P&L to retained earnings.

CREATE TABLE IF NOT EXISTS shop_fiscal_years (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(80) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opening_transferred BOOLEAN NOT NULL DEFAULT FALSE,
    close_voucher_id BIGINT,
    closed_at TIMESTAMP,
    closed_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shop_fiscal_year_code UNIQUE (tenant_id, shop_id, code),
    CONSTRAINT chk_shop_fiscal_year_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_shop_fiscal_year_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_shop_fiscal_years_shop_dates
    ON shop_fiscal_years (tenant_id, shop_id, start_date, end_date);

CREATE TABLE IF NOT EXISTS shop_voucher_sequences (
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    fiscal_year_id BIGINT NOT NULL REFERENCES shop_fiscal_years (id),
    voucher_type VARCHAR(30) NOT NULL,
    next_number INT NOT NULL DEFAULT 1,
    PRIMARY KEY (tenant_id, shop_id, fiscal_year_id, voucher_type)
);

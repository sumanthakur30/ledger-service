-- Operational expense / other-income capture (posts into ledger_vouchers).
-- Categories and income types are config-seeded per shop — not hardcoded enums in business logic.

CREATE TABLE IF NOT EXISTS expense_category (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES expense_category (id),
    ledger_account_id BIGINT NOT NULL REFERENCES ledger_accounts (id),
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    system_seed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expense_category_shop_code UNIQUE (tenant_id, shop_id, code)
);

CREATE INDEX IF NOT EXISTS idx_expense_category_shop
    ON expense_category (tenant_id, shop_id, active, sort_order);

CREATE TABLE IF NOT EXISTS income_type (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    ledger_account_id BIGINT NOT NULL REFERENCES ledger_accounts (id),
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    system_seed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_income_type_shop_code UNIQUE (tenant_id, shop_id, code)
);

CREATE INDEX IF NOT EXISTS idx_income_type_shop
    ON income_type (tenant_id, shop_id, active, sort_order);

CREATE TABLE IF NOT EXISTS expense_entry (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    entry_date DATE NOT NULL,
    category_id BIGINT NOT NULL REFERENCES expense_category (id),
    amount NUMERIC(14, 2) NOT NULL,
    tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    payment_mode VARCHAR(20) NOT NULL,
    cash_account_code VARCHAR(40),
    vendor_name VARCHAR(200),
    vendor_party_id BIGINT,
    narration VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    requested_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    voucher_id BIGINT,
    source_type VARCHAR(40) NOT NULL DEFAULT 'EXPENSE_ENTRY',
    recurring_id BIGINT,
    attachment_uri VARCHAR(500),
    branch_shop_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_expense_entry_shop_date
    ON expense_entry (tenant_id, shop_id, entry_date DESC);

CREATE INDEX IF NOT EXISTS idx_expense_entry_shop_status
    ON expense_entry (tenant_id, shop_id, status);

CREATE INDEX IF NOT EXISTS idx_expense_entry_category
    ON expense_entry (category_id);

CREATE TABLE IF NOT EXISTS other_income_entry (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    entry_date DATE NOT NULL,
    income_type_code VARCHAR(40) NOT NULL,
    ledger_account_id BIGINT NOT NULL REFERENCES ledger_accounts (id),
    amount NUMERIC(14, 2) NOT NULL,
    payment_mode VARCHAR(20) NOT NULL,
    cash_account_code VARCHAR(40),
    payer_name VARCHAR(200),
    narration VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    voucher_id BIGINT,
    source_type VARCHAR(40) NOT NULL DEFAULT 'OTHER_INCOME',
    branch_shop_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_other_income_shop_date
    ON other_income_entry (tenant_id, shop_id, entry_date DESC);

CREATE INDEX IF NOT EXISTS idx_other_income_shop_status
    ON other_income_entry (tenant_id, shop_id, status);

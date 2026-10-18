INSERT INTO ledger_accounts (
    tenant_id, shop_id, code, name, account_type, active, system_account, created_at, updated_at)
SELECT DISTINCT existing.tenant_id,
       existing.shop_id,
       '5600',
       'Sales promotion / free samples',
       'EXPENSE',
       TRUE,
       TRUE,
       NOW(),
       NOW()
FROM ledger_accounts existing
WHERE NOT EXISTS (
    SELECT 1
    FROM ledger_accounts present
    WHERE present.tenant_id = existing.tenant_id
      AND present.shop_id = existing.shop_id
      AND present.code = '5600'
);

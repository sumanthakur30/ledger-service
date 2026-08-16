package com.shopmanagement.ledgerservice.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.CreateAccountRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;

@Service
public class ChartOfAccountsService {

    /** code, name, type, optional parent code */
    private static final String[][] DEFAULTS = {
            {"1000", "Cash", "ASSET", ""},
            {"1010", "Bank", "ASSET", ""},
            {"1100", "Sundry Debtors", "ASSET", ""},
            {"1200", "Stock / Inventory", "ASSET", ""},
            {"2000", "Sundry Creditors", "LIABILITY", ""},
            {"2100", "GST Payable", "LIABILITY", ""},
            {"2110", "Output CGST", "LIABILITY", "2100"},
            {"2120", "Output SGST", "LIABILITY", "2100"},
            {"2130", "Output IGST", "LIABILITY", "2100"},
            {"2200", "GST Input Credit", "ASSET", ""},
            {"2210", "Input CGST", "ASSET", "2200"},
            {"2220", "Input SGST", "ASSET", "2200"},
            {"2230", "Input IGST", "ASSET", "2200"},
            {"3000", "Capital", "EQUITY", ""},
            {"3100", "Retained earnings", "EQUITY", ""},
            {"4000", "Sales", "INCOME", ""},
            {"4100", "Interest Income", "INCOME", ""},
            {"5000", "Purchase", "EXPENSE", ""},
            {"5100", "Freight / Landed charges", "EXPENSE", ""},
            {"5200", "Discount allowed", "EXPENSE", ""},
            {"5300", "Cost of goods sold", "EXPENSE", ""},
            {"5400", "Stock write-off / Dump-Brk-Exp", "EXPENSE", ""},
            {"5500", "Collection centre / outsource expense", "EXPENSE", ""},
            {"2300", "TDS Payable", "LIABILITY", ""}
    };

    private final LedgerAccountRepository accountRepository;

    public ChartOfAccountsService(LedgerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public java.util.List<LedgerAccount> list() {
        requireManageOrders();
        return accountRepository.findByTenantIdAndShopIdOrderByCodeAsc(requireTenantId(), requireShopId());
    }

    @Transactional
    public LedgerAccount create(CreateAccountRequest request) {
        requireManageOrders();
        if (request == null || blank(request.getCode()) || blank(request.getName()) || blank(request.getAccountType())) {
            throw new IllegalArgumentException("code, name and accountType are required");
        }
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        String type = request.getAccountType().trim().toUpperCase(Locale.ROOT);
        validateType(type);
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        if (accountRepository.existsByTenantIdAndShopIdAndCode(tenantId, shopId, code)) {
            throw new IllegalArgumentException("Account code already exists: " + code);
        }
        LedgerAccount account = new LedgerAccount();
        account.setTenantId(tenantId);
        account.setShopId(shopId);
        account.setCode(code);
        account.setName(request.getName().trim());
        account.setAccountType(type);
        account.setParentId(request.getParentId());
        account.setActive(Boolean.TRUE);
        account.setSystemAccount(Boolean.FALSE);
        return accountRepository.save(account);
    }

    @Transactional
    public java.util.List<LedgerAccount> seedDefaults() {
        requireManageOrders();
        return ensureSystemDefaults();
    }

    /**
     * Idempotent system CoA seed for auto-posting vouchers. Does not require MANAGE_ORDERS
     * so clinic/lab/pharmacy bills can post when the operator only has department permissions.
     */
    @Transactional
    public java.util.List<LedgerAccount> ensureSystemDefaults() {
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        for (String[] row : DEFAULTS) {
            if (accountRepository.existsByTenantIdAndShopIdAndCode(tenantId, shopId, row[0])) {
                continue;
            }
            LedgerAccount account = new LedgerAccount();
            account.setTenantId(tenantId);
            account.setShopId(shopId);
            account.setCode(row[0]);
            account.setName(row[1]);
            account.setAccountType(row[2]);
            account.setActive(Boolean.TRUE);
            account.setSystemAccount(Boolean.TRUE);
            accountRepository.save(account);
        }
        linkDefaultParents(tenantId, shopId);
        return accountRepository.findByTenantIdAndShopIdOrderByCodeAsc(tenantId, shopId);
    }

    private void linkDefaultParents(Long tenantId, String shopId) {
        for (String[] row : DEFAULTS) {
            if (row.length < 4 || row[3] == null || row[3].isBlank()) {
                continue;
            }
            LedgerAccount child = accountRepository.findByTenantIdAndShopIdAndCode(tenantId, shopId, row[0]).orElse(null);
            LedgerAccount parent = accountRepository.findByTenantIdAndShopIdAndCode(tenantId, shopId, row[3]).orElse(null);
            if (child == null || parent == null || child.getParentId() != null) {
                continue;
            }
            child.setParentId(parent.getId());
            accountRepository.save(child);
        }
    }

    static void validateType(String type) {
        if (!java.util.Set.of("ASSET", "LIABILITY", "INCOME", "EXPENSE", "EQUITY").contains(type)) {
            throw new IllegalArgumentException("accountType must be ASSET|LIABILITY|INCOME|EXPENSE|EQUITY");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Long requireTenantId() {
        Long tenantId = RequestIdFilter.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return tenantId;
    }

    private String requireShopId() {
        String shopId = RequestIdFilter.getCurrentShopId();
        if (shopId == null || shopId.isBlank()) {
            throw new IllegalStateException("Missing shop context");
        }
        return shopId;
    }

    private void requireManageOrders() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role)
                || "TRADE_ACCOUNTANT".equals(role) || "TRADE_PHARMACIST".equals(role)) {
            return;
        }
        var perms = RequestIdFilter.getCurrentPermissions();
        if (perms.contains("MANAGE_ORDERS")
                || perms.contains("MANAGE_STOCKS")
                || perms.contains("PROCUREMENT_VIEW")
                || perms.contains("PROCUREMENT_FINANCE")) {
            return;
        }
        throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
    }
}

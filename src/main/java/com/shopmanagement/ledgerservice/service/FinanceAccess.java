package com.shopmanagement.ledgerservice.service;

import java.util.List;

import com.shopmanagement.ledgerservice.filter.RequestIdFilter;

/** Shared tenant/shop/permission helpers for expense-module services. */
final class FinanceAccess {

    private FinanceAccess() {}

    static Long requireTenantId() {
        Long tenantId = RequestIdFilter.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return tenantId;
    }

    static String requireShopId() {
        String shopId = RequestIdFilter.getCurrentShopId();
        if (shopId == null || shopId.isBlank()) {
            throw new IllegalStateException("Missing shop context");
        }
        return shopId;
    }

    static void requireFinanceAccess() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role)
                || "TRADE_ACCOUNTANT".equals(role) || "TRADE_PHARMACIST".equals(role)) {
            return;
        }
        List<String> perms = RequestIdFilter.getCurrentPermissions();
        if (perms.contains("MANAGE_FINANCE")
                || perms.contains("MANAGE_ORDERS")
                || perms.contains("PROCUREMENT_FINANCE")) {
            return;
        }
        throw new SecurityException("Forbidden: missing permission MANAGE_FINANCE or MANAGE_ORDERS");
    }

    static String currentUsername() {
        String user = RequestIdFilter.getCurrentUsername();
        return user != null && !user.isBlank() ? user.trim() : null;
    }
}

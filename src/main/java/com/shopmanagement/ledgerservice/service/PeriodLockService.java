package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.ShopPeriodLock;
import com.shopmanagement.ledgerservice.repository.ShopPeriodLockRepository;

/** Light accounting period lock — reject vouchers dated on or before lockedThrough. */
@Service
public class PeriodLockService {

    private final ShopPeriodLockRepository repository;
    private final FiscalYearService fiscalYearService;

    public PeriodLockService(ShopPeriodLockRepository repository, @Lazy FiscalYearService fiscalYearService) {
        this.repository = repository;
        this.fiscalYearService = fiscalYearService;
    }

    @Transactional(readOnly = true)
    public LocalDate getLockedThrough() {
        requireManageOrders();
        return repository
                .findByTenantIdAndShopId(requireTenantId(), requireShopId())
                .map(ShopPeriodLock::getLockedThrough)
                .orElse(null);
    }

    @Transactional
    public LocalDate setLockedThrough(LocalDate lockedThrough) {
        requireManageOrders();
        if (lockedThrough == null) {
            throw new IllegalArgumentException("lockedThrough is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        ShopPeriodLock lock = repository.findByTenantIdAndShopId(tenantId, shopId).orElseGet(ShopPeriodLock::new);
        lock.setTenantId(tenantId);
        lock.setShopId(shopId);
        lock.setLockedThrough(lockedThrough);
        lock.setUpdatedAt(LocalDateTime.now());
        lock.setUpdatedBy(RequestIdFilter.getCurrentRole());
        return repository.save(lock).getLockedThrough();
    }

    @Transactional
    public void clearLock() {
        requireManageOrders();
        repository.findByTenantIdAndShopId(requireTenantId(), requireShopId()).ifPresent(repository::delete);
    }

    /** Throws if voucherDate is on or before the shop's lockedThrough date. */
    @Transactional(readOnly = true)
    public void assertOpen(LocalDate voucherDate) {
        LocalDate date = voucherDate != null ? voucherDate : LocalDate.now();
        Long tenantId = RequestIdFilter.getCurrentTenantId();
        String shopId = RequestIdFilter.getCurrentShopId();
        if (tenantId == null || shopId == null || shopId.isBlank()) {
            return;
        }
        repository.findByTenantIdAndShopId(tenantId, shopId).ifPresent(lock -> {
            if (lock.getLockedThrough() != null && !date.isAfter(lock.getLockedThrough())) {
                throw new IllegalArgumentException(
                        "Accounting period locked through " + lock.getLockedThrough()
                                + " — cannot post voucher dated " + date);
            }
        });
        fiscalYearService.assertNotClosed(date);
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
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role) || "TRADE_ACCOUNTANT".equals(role)) {
            return;
        }
        var perms = RequestIdFilter.getCurrentPermissions();
        if (perms.contains("MANAGE_ORDERS") || perms.contains("MANAGE_FINANCE")) {
            return;
        }
        throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
    }
}

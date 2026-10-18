package com.shopmanagement.ledgerservice.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherLineRepository;

/** Light bank/cash reconciliation tick on voucher lines (no parallel books). */
@Service
public class BankReconciliationService {

    private final LedgerVoucherLineRepository lineRepository;

    public BankReconciliationService(LedgerVoucherLineRepository lineRepository) {
        this.lineRepository = lineRepository;
    }

    @Transactional
    public LedgerVoucherLine setReconciled(Long lineId, boolean reconciled) {
        requireManageOrders();
        LedgerVoucherLine line = lineRepository
                .findScoped(lineId, requireTenantId(), requireShopId())
                .orElseThrow(() -> new IllegalArgumentException("Voucher line not found: " + lineId));
        line.setReconciled(reconciled);
        line.setReconciledAt(reconciled ? LocalDateTime.now() : null);
        return lineRepository.save(line);
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
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role)) {
            return;
        }
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")) {
            throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
        }
    }
}

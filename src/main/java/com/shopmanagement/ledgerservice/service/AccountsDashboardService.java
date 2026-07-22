package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.AccountsDashboardResponse;
import com.shopmanagement.ledgerservice.dto.AccountsDashboardResponse.RecentVoucherRow;
import com.shopmanagement.ledgerservice.dto.TrialBalanceRow;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

@Service
public class AccountsDashboardService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerVoucherRepository voucherRepository;
    private final VoucherService voucherService;

    public AccountsDashboardService(
            LedgerAccountRepository accountRepository,
            LedgerVoucherRepository voucherRepository,
            VoucherService voucherService) {
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.voucherService = voucherService;
    }

    @Transactional(readOnly = true)
    public AccountsDashboardResponse dashboard(LocalDate from, LocalDate to) {
        requireManageOrders();
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        LocalDate fromDate = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null ? to : LocalDate.now();

        List<LedgerAccount> accounts = accountRepository.findByTenantIdAndShopIdOrderByCodeAsc(tenantId, shopId);
        List<LedgerVoucher> periodVouchers =
                voucherRepository.search(tenantId, shopId, fromDate, toDate, "");
        List<LedgerVoucher> allRecent = voucherRepository.searchLimited(
                tenantId,
                shopId,
                LocalDate.of(2000, 1, 1),
                LocalDate.of(2100, 12, 31),
                "",
                Pageable.ofSize(10));

        int posted = 0;
        int draft = 0;
        double periodDebit = 0;
        double periodCredit = 0;
        for (LedgerVoucher v : periodVouchers) {
            String status = v.getStatus() != null ? v.getStatus().toUpperCase(Locale.ROOT) : "";
            if ("POSTED".equals(status)) {
                posted++;
                periodDebit += safe(v.getTotalDebit());
                periodCredit += safe(v.getTotalCredit());
            } else if ("DRAFT".equals(status)) {
                draft++;
            }
        }

        List<TrialBalanceRow> tb = voucherService.trialBalance(toDate);
        double debtors = 0;
        double creditors = 0;
        double inventory = 0;
        double sales = 0;
        double gst = 0;
        List<TrialBalanceRow> highlight = new ArrayList<>();
        for (TrialBalanceRow row : tb) {
            String code = row.code() != null ? row.code() : "";
            double netDr = Math.max(0, round2(row.debit() - row.credit()));
            double netCr = Math.max(0, round2(row.credit() - row.debit()));
            if ("1100".equals(code)) {
                debtors = netDr;
            } else if ("2000".equals(code)) {
                creditors = netCr;
            } else if ("1200".equals(code)) {
                inventory = netDr;
            } else if ("4000".equals(code)) {
                sales = netCr;
            } else if ("2100".equals(code)) {
                gst = netCr;
            }
            if (highlight.size() < 8 && (row.debit() > 0.009 || row.credit() > 0.009)) {
                highlight.add(row);
            }
        }

        List<RecentVoucherRow> recent = new ArrayList<>();
        for (LedgerVoucher v : allRecent) {
            recent.add(new RecentVoucherRow(
                    v.getId(),
                    v.getVoucherNumber(),
                    v.getVoucherType(),
                    v.getStatus(),
                    v.getSourceType(),
                    v.getSourceId(),
                    safe(v.getTotalDebit()),
                    v.getVoucherDate() != null ? v.getVoucherDate().toString() : null));
        }

        return new AccountsDashboardResponse(
                accounts.size(),
                posted,
                draft,
                round2(periodDebit),
                round2(periodCredit),
                round2(debtors),
                round2(creditors),
                round2(inventory),
                round2(sales),
                round2(gst),
                highlight,
                recent);
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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

package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.StockWriteOffVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/** Dump / Brk / Exp write-off: Dr 5400 Write-off, Cr 1200 Stock. */
@Service
public class StockWriteOffVoucherService {

    public static final String SOURCE_STOCK_WRITE_OFF = "STOCK_WRITE_OFF";
    public static final String CODE_STOCK = "1200";
    public static final String CODE_WRITE_OFF = "5400";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final PeriodLockService periodLockService;

    public StockWriteOffVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService,
            PeriodLockService periodLockService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.periodLockService = periodLockService;
    }

    @Transactional
    public LedgerVoucher postFromStockWriteOff(StockWriteOffVoucherRequest request) {
        requireManageOrders();
        if (request == null || request.getWriteOffId() == null) {
            throw new IllegalArgumentException("writeOffId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_STOCK_WRITE_OFF, request.getWriteOffId());
        if (existing.isPresent()) {
            return existing.get();
        }

        double amount = round2(safe(request.getAmount()));
        if (amount <= 0) {
            throw new IllegalArgumentException("Write-off amount must be greater than zero");
        }

        LocalDate voucherDate = request.getWriteOffDate() != null ? request.getWriteOffDate() : LocalDate.now();
        periodLockService.assertOpen(voucherDate);

        chartOfAccountsService.seedDefaults();
        LedgerAccount writeOff = requireAccount(tenantId, shopId, CODE_WRITE_OFF);
        LedgerAccount stock = requireAccount(tenantId, shopId, CODE_STOCK);

        String doc = request.getWriteOffNumber() != null && !request.getWriteOffNumber().isBlank()
                ? request.getWriteOffNumber().trim()
                : ("WO-" + request.getWriteOffId());

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("WO-" + request.getWriteOffId());
        voucher.setVoucherDate(voucherDate);
        voucher.setVoucherType("JOURNAL");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_STOCK_WRITE_OFF);
        voucher.setSourceId(request.getWriteOffId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Stock write-off " + doc);

        voucher.addLine(line(writeOff.getId(), amount, 0, "Write-off " + doc, 1));
        voucher.addLine(line(stock.getId(), 0, amount, "Stock clear " + doc, 2));
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Write-off voucher not balanced: debit %.2f credit %.2f",
                    voucher.getTotalDebit(),
                    voucher.getTotalCredit()));
        }
        LedgerVoucher saved = voucherRepository.save(voucher);
        return voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(saved.getId(), tenantId, shopId)
                .orElse(saved);
    }

    private LedgerAccount requireAccount(Long tenantId, String shopId, String code) {
        return accountRepository
                .findByTenantIdAndShopIdAndCode(tenantId, shopId, code)
                .orElseThrow(() -> new IllegalArgumentException("Missing ledger account code " + code));
    }

    private static LedgerVoucherLine line(Long accountId, double debit, double credit, String narration, int lineNo) {
        LedgerVoucherLine line = new LedgerVoucherLine();
        line.setAccountId(accountId);
        line.setDebit(round2(debit));
        line.setCredit(round2(credit));
        line.setLineNarration(narration);
        line.setLineNo(lineNo);
        return line;
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
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")
                && !RequestIdFilter.getCurrentPermissions().contains("MANAGE_STOCKS")) {
            throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS or MANAGE_STOCKS");
        }
    }
}

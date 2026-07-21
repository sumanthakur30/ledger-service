package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.SalesReturnVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Maps trade sales returns to posted credit-note journals:
 * Dr Sales (4000) + Dr GST Payable (2100); Cr Debtors (1100).
 * When tax split is unknown, the full amount credits debtors and debits sales.
 */
@Service
public class SalesReturnVoucherService {

    public static final String SOURCE_SALES_RETURN = "SALES_RETURN";
    public static final String CODE_DEBTORS = "1100";
    public static final String CODE_SALES = "4000";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public SalesReturnVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromSalesReturn(SalesReturnVoucherRequest request) {
        requireManageOrders();
        if (request == null || request.getSalesReturnId() == null) {
            throw new IllegalArgumentException("salesReturnId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_SALES_RETURN, request.getSalesReturnId());
        if (existing.isPresent()) {
            return existing.get();
        }

        double total = round2(safe(request.getTotalAmount()));
        if (total <= 0) {
            throw new IllegalArgumentException("Return total must be greater than zero");
        }

        chartOfAccountsService.seedDefaults();
        LedgerAccount debtors = requireAccount(tenantId, shopId, CODE_DEBTORS);
        LedgerAccount sales = requireAccount(tenantId, shopId, CODE_SALES);

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setVoucherNumber("CN-" + request.getSalesReturnId());
        voucher.setVoucherDate(request.getReturnDate() != null ? request.getReturnDate() : LocalDate.now());
        voucher.setVoucherType("CREDIT_NOTE");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_SALES_RETURN);
        voucher.setSourceId(request.getSalesReturnId());
        String cn = request.getCreditNoteNumber() != null && !request.getCreditNoteNumber().isBlank()
                ? request.getCreditNoteNumber().trim()
                : (request.getReturnNumber() != null ? request.getReturnNumber() : String.valueOf(request.getSalesReturnId()));
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Sales return credit note " + cn);

        int lineNo = 1;
        voucher.addLine(line(sales.getId(), total, 0, "Sales return " + cn, lineNo++));
        voucher.addLine(line(debtors.getId(), 0, total, "AR reverse " + cn, lineNo));
        voucher.setTotalDebit(total);
        voucher.setTotalCredit(total);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Sales return voucher not balanced: debit %.2f credit %.2f",
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
                .orElseThrow(() -> new IllegalArgumentException("Missing ledger account code " + code + " — seed defaults first"));
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
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")) {
            throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
        }
    }
}

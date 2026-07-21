package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.SupplierPaymentVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Maps supplier AP payments to posted payment vouchers:
 * Dr Creditors (2000); Cr Cash (1000) or Bank (1010).
 */
@Service
public class SupplierPaymentVoucherService {

    public static final String SOURCE_SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT";
    public static final String CODE_CASH = "1000";
    public static final String CODE_BANK = "1010";
    public static final String CODE_CREDITORS = "2000";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public SupplierPaymentVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromSupplierPayment(SupplierPaymentVoucherRequest request) {
        requireManageOrders();
        if (request == null || request.getPaymentId() == null) {
            throw new IllegalArgumentException("paymentId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_SUPPLIER_PAYMENT, request.getPaymentId());
        if (existing.isPresent()) {
            return existing.get();
        }

        double amount = round2(safe(request.getAmount()));
        if (amount <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        chartOfAccountsService.seedDefaults();
        String method = request.getPaymentMethod() != null
                ? request.getPaymentMethod().trim().toUpperCase(Locale.ROOT)
                : "CASH";
        String cashBankCode = "CASH".equals(method) ? CODE_CASH : CODE_BANK;
        LedgerAccount cashOrBank = requireAccount(tenantId, shopId, cashBankCode);
        LedgerAccount creditors = requireAccount(tenantId, shopId, CODE_CREDITORS);

        String doc = request.getDocumentNumber() != null && !request.getDocumentNumber().isBlank()
                ? request.getDocumentNumber().trim()
                : ("PAY-" + request.getPaymentId());
        String voucherNo = "SP-" + request.getPaymentId();

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setVoucherNumber(voucherNo);
        voucher.setVoucherDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
        voucher.setVoucherType("PAYMENT");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_SUPPLIER_PAYMENT);
        voucher.setSourceId(request.getPaymentId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Supplier payment " + voucherNo + " for " + doc + " (" + method + ")");

        voucher.addLine(line(creditors.getId(), amount, 0, "AP clear " + doc, 1));
        voucher.addLine(line(cashOrBank.getId(), 0, amount, method + " " + doc, 2));
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Supplier payment voucher not balanced: debit %.2f credit %.2f",
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
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing ledger account code " + code + " — seed defaults first"));
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
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role) || "TRADE_ACCOUNTANT".equals(role)) {
            return;
        }
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")
                && !RequestIdFilter.getCurrentPermissions().contains("PROCUREMENT_FINANCE")) {
            throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
        }
    }
}
